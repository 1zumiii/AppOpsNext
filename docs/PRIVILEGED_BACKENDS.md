# Privileged backends

AppOpsNext needs a process running with Android's shell identity to read and
write system AppOps. Shizuku supplies that identity, while AppOpsNext owns the
command protocol and backend selection.

## Selection order

1. The bundled native daemon is installed and started through Shizuku's remote
   process bridge. Runtime requests use private stdin/stdout pipes and do not
   depend on a UserService connection callback.
2. If native startup or its handshake fails, AppOpsNext automatically tries the
   Shizuku UserService backend.
3. If both paths fail, privileged features remain unavailable and diagnostics
   preserve the failing backend and event details.

The native path is the primary backend because it keeps the privileged command
surface small, avoids large Binder responses, and works around the observed
UserService callback failure. The remote-process API is deprecated upstream,
so its use remains isolated under `shizuku/process` and the UserService path is
kept as a functional fallback.

## Command surface

Both backends expose the same fixed set of commands; neither runs an arbitrary
shell string. Each command is an argument list. The app validates the package,
operation and permission names first, and the daemon validates them again.

| Command | Runs |
| --- | --- |
| `GET_PACKAGE_OPS` | `cmd appops get <package>` |
| `GET_PACKAGE_OP` | `cmd appops get <package> <op>` |
| `GET_UID_OPS` | `cmd appops get <uid>` |
| `GET_OP_STATES` | `dumpsys appops --op <op>` |
| `GET_UID_STATES` | `dumpsys appops --package <package>` |
| `GET_WATCHERS` | `dumpsys appops --watchers` (no arguments) |
| `GET_HISTORY` | `dumpsys appops --history --include-discrete 0 --op <op>` |
| `SET_PACKAGE` | `cmd appops set <package> <op> <mode>` |
| `SET_UID` | `cmd appops set --uid <package> <op> <mode>` |
| `GET_PACKAGE_PERMISSIONS` | `dumpsys package <package>` |
| `LIST_USERS` | `pm list users` (no arguments) |
| `GET_PACKAGE_TIMES` | `dumpsys package packages` (no arguments) |
| `LIST_PACKAGES_FOR_USER` | `pm list packages --user <user> -U` |
| `GET_USER_PACKAGE_OPS` | `cmd appops get --user <user> <package>` |
| `GET_USER_PACKAGE_OP` | `cmd appops get --user <user> <package> <op>` |
| `SET_USER_PACKAGE` | `cmd appops set --user <user> <package> <op> <mode>` |
| `SET_USER_UID` | `cmd appops set --user <user> --uid <package> <op> <mode>` |
| `SET_REVOKED_COMPAT` | `cmd package set-permission-flags --user <user> <package> <permission> revoked-compat` |
| `CLEAR_REVOKED_COMPAT` | `cmd package clear-permission-flags --user <user> <package> <permission> revoked-compat` |

The user commands let the app list, app detail and permission browser work
on another Android user, such as a work profile or an app clone. `<user>` is a
canonical non-negative integer, validated by both the app and the daemon. They
are appended AIDL methods 15 to 20, and the commands above them are unchanged.
Binder reads for another user take the uid from the app list
(`LIST_PACKAGES_FOR_USER`), or derive it as `userId * 100000 + appId`. Templates,
new-app automation, the setting guard and history stay on the current user.

`GET_UID_STATES` exists for the monitor's off-screen filter and returns the
process state AppOps keeps for each UID carrying that package. It is scoped to
one package deliberately: the unfiltered dump runs to tens of thousands of
lines on an ordinary device, which is not something to run while callbacks are
arriving. A ROM that does not support `--package`, or prints the block in a
different shape, yields no usable state; the monitor then reports the access
rather than dropping it.

`GET_OP_STATES` supplies the by-permission view. It lists every UID and package
mode for one operation in one call.

`GET_PACKAGE_PERMISSIONS` and the two `REVOKED_COMPAT` commands are for Android 16
builds that block AppOps writes to runtime-permission ops. For details, see
[device findings](DEVICE_FINDINGS.md#runtime-permission-app-op-mapping-on-android-16).

`GET_WATCHERS` uses the platform's watcher-only option, avoiding a full history
dump or a fixed line cutoff. It is exposed as appended AIDL method 11; existing
transaction numbers stay unchanged. An unsupported option, malformed response,
timeout or missing match cannot pass the monitor's registry check. The parser
also supplies the experimental mode-listener snapshot and diagnostic report.

## Binder reads

Reads can also go directly to `IAppOpsService` through Shizuku's binder
forwarding (`appops/binder/`), the same route the monitor uses for its watch
callbacks. Writes always use the commands above.

| Capability | Binder calls | Replaces |
| --- | --- | --- |
| `PACKAGE_OPS` | `getUidOps`, `getOpsForPackage` | `GET_PACKAGE_OPS`, `GET_PACKAGE_OP` |
| `OP_STATES` | `getPackagesForOps`, then `getUidOps` per listed app | `GET_OP_STATES` |
| `OP_LIST` | `AppOpsManager.getNumOps`, `opToName`, `opToPublicName` | The built-in operation table |

Replies are unparcelled with the platform's own `AppOpsManager.PackageOps`
creator, so the wire format is always the running system's. The hidden-API
policy is lifted for `AppOpsManager`, `ServiceManager` and `IAppOpsService`
with the HiddenApiBypass library; if that fails, plain reflection is tried.

A binder read replaces a text read only after `AppOpsReadProbe` confirms it on
this device. The probe reads the same state both ways and enables a
capability only when the results agree: the app's own package and Settings
for `PACKAGE_OPS`, every installed app's effective `CAMERA` mode for
`OP_STATES`. The result is stored per `Build.FINGERPRINT` and app version, so
an OTA or an app update probes again. A binder failure at run time turns the
capability off for the process, drops the stored result, and the read falls
back to text. The diagnostic report shows the path of each read on the
`reads=` line.

Transaction codes are read from `IAppOpsService$Stub` when the platform
allows it. The fallback is the method's position in the platform sources for
the API level: Android 17 moved `checkOperationForDevice` to position 12,
which shifted positions 12 to 54 by one. The fallback cannot follow a vendor
that reorders the interface; the probe catches that for reads.

## Compatibility evidence

| Device | System | Evidence |
| --- | --- | --- |
| ASUS AI2302 | Android 15 / API 35 | Project reference device; native connection, reads, writes, history, templates, and permission enforcement manually tested. |
| Xiaomi 24117RN76G | HyperOS 3 / Android 16 / API 36 | Independent issue reporter confirmed a shell-UID native connection, successful self-check, app listing, permission history, AppOps reads and writes, and camera enforcement. |
| realme RMX3370 | Android 16 QPR2 / API 36 | Independent issue reporter confirmed that this build blocks runtime-permission AppOps writes. Ignore takes effect through the `REVOKED_COMPAT` permission flag. |

This evidence establishes working compatibility for the tested devices. It is
not a general claim for every Android 16 device or OEM ROM.

## Output format check on emulators

The `Device output formats` workflow (`.github/workflows/device-formats.yml`)
catches AOSP changes to the text that the parsers read. It runs weekly, on
demand, and on pull requests that change the check itself. For each Google
APIs x86_64 emulator image in its matrix (API 35, 36, 36.1 and 37.0), it does
these steps:

1. `scripts/capture-device-dumps.sh` connects through `adb shell`, which has
   the same shell identity as the daemon. It sets a package mode
   (`RUN_IN_BACKGROUND`), a UID mode (`RUN_ANY_IN_BACKGROUND`) and the
   `REVOKED_COMPAT` flag on `CAMERA`, and starts and stops `CAMERA` once to
   create history. It reads the history right away, then runs the other read
   commands in the table above.
2. `DeviceDumpCompatibilityTest` runs the production parsers over the
   captured files. It checks that each dump header is recognized and that the
   parsers find the states from step 1. When `APPOPSNEXT_DEVICE_DUMPS` is not
   set, the test is skipped, so ordinary unit test runs do not change.
3. The workflow uploads the dumps, the device logcat and the test report as
   an artifact, also when a step fails. If a dump fails because
   system_server died, the script waits for the system to come back and
   tries once more. The test fails if the second attempt also fails.

To check a real device, connect it and run:

```sh
scripts/capture-device-dumps.sh build/device-dumps/my-device
APPOPSNEXT_DEVICE_DUMPS=$PWD/build/device-dumps/my-device \
  ./gradlew :app:testDebugUnitTest --tests '*DeviceDumpCompatibilityTest'
```

The script restores the changed modes and the flag when it exits.

Limits:

- The check covers AOSP emulator images only. OEM ROMs, such as the ColorOS
  build in issue #13, can print other formats, so device reports are still
  necessary.
- It covers the shell text path only. A structured Binder read runs through
  Shizuku and cannot be exercised by `adb shell`.
- Writes are applied with the shell `cmd` tools, not through either backend.

## Known Android 16 / HyperOS failure boundary

On the reported Xiaomi device, Shizuku's Binder was available as UID 2000 and
AppOpsNext authorization succeeded. The UserService bind request returned, but
the service connection callback did not arrive before the 12-second timeout.
The native daemon launched through the same Shizuku installation and completed
AppOps operations successfully.

The exact cause is therefore still unknown. Available evidence narrows the
failure to the UserService startup or callback path on that environment; it
does not establish whether Android 16, HyperOS, Shizuku, or their interaction is
responsible. Do not encode an OEM-specific workaround without reproducible
evidence.

## External ADB daemon decision

A manually started daemon that does not use Shizuku is intentionally not
implemented. It would still require ADB, wireless debugging, or root to obtain
the shell identity, and would add a startup command, daemon discovery and
authentication, lifecycle recovery, protocol versioning, and multi-user
handling. Users would normally need to repeat startup after every reboot.

That extra path does not address the observed failure: Shizuku can already
start the native daemon on the affected device. A fully Shizuku-independent
app cannot grant itself shell privileges; "independent" would only move the
external bootstrap responsibility to ADB or root.

Reconsider an external ADB transport only when evidence shows at least one of
these conditions:

- Shizuku can no longer expose its remote-process bridge on a supported device.
- Both the native bootstrap and UserService fail while the same AppOps commands
  work from an ADB shell.
- Reproducible affected-device testing justifies the additional installation,
  authentication, lifecycle, and multi-user surface.

Until then, native-through-Shizuku plus UserService fallback is the supported
backend strategy.
