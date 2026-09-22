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
shell string. Each one is built as an argument list with the package and
operation names validated first, on the app side and again in the daemon.

| Command | Runs |
| --- | --- |
| `GET_PACKAGE_OPS` | `cmd appops get <package>` |
| `GET_PACKAGE_OP` | `cmd appops get <package> <op>` |
| `GET_UID_OPS` | `cmd appops get <uid>` |
| `GET_UID_STATES` | `dumpsys appops --package <package>` |
| `GET_WATCHERS` | `dumpsys appops --watchers` (no arguments) |
| `GET_HISTORY` | `dumpsys appops --history --include-discrete 0 --op <op>` |
| `SET_PACKAGE` | `cmd appops set <package> <op> <mode>` |
| `SET_UID` | `cmd appops set --uid <package> <op> <mode>` |

`GET_UID_STATES` exists for the monitor's off-screen filter and returns the
process state AppOps keeps for each UID carrying that package. It is scoped to
one package deliberately: the unfiltered dump runs to tens of thousands of
lines on an ordinary device, which is not something to run while callbacks are
arriving. A ROM that does not support `--package`, or prints the block in a
different shape, yields no usable state; the monitor then reports the access
rather than dropping it.

`GET_WATCHERS` uses the platform's watcher-only option, avoiding a full history
dump or a fixed line cutoff. It is exposed as appended AIDL method 11; existing
transaction numbers stay unchanged. An unsupported option, malformed response,
timeout or missing match cannot pass the monitor's registry check. The parser
also supplies the experimental mode-listener snapshot and diagnostic report.

## Compatibility evidence

| Device | System | Evidence |
| --- | --- | --- |
| ASUS AI2302 | Android 15 / API 35 | Project reference device; native connection, reads, writes, history, templates, and permission enforcement manually tested. |
| Xiaomi 24117RN76G | HyperOS 3 / Android 16 / API 36 | Independent issue reporter confirmed a shell-UID native connection, successful self-check, app listing, permission history, AppOps reads and writes, and camera enforcement. |

This evidence establishes working compatibility for the tested devices. It is
not a general claim for every Android 16 device or OEM ROM.

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
