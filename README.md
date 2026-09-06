# AppOpsNext

**English** | [简体中文](README.zh-CN.md)

Manage Android AppOps, reuse permission templates, and review system permission
history — with a native Kotlin and Jetpack Compose interface powered by
[Shizuku](https://shizuku.rikka.app/).

[Download APK](https://github.com/1zumiii/AppOpsNext/releases/latest) ·
[Report an issue](https://github.com/1zumiii/AppOpsNext/issues) ·
[Build status](https://github.com/1zumiii/AppOpsNext/actions/workflows/ci.yml)

## What you can do

| Area | Features |
| --- | --- |
| Applications | Browse current-user apps, search names and packages, and choose whether to show system apps. |
| AppOps | Inspect package and UID modes, search operations by localized or system name, and verify changes by reading them back. |
| Templates and batches | Create reusable rules, reorder them, apply a template to multiple apps, or change several operations in one app. |
| Newly installed apps | Opt in to automatic template application, catch up on pending installations, and inspect saved per-rule results. |
| History | Explore permission distribution, app statistics, and timelines; choose and reorder the operations you follow. |
| Settings and diagnostics | Switch between English, Simplified Chinese, or the system language, and inspect connection status and diagnostic reports. |

## Install and get started

### Requirements

- **Android 15 or later** (API 35+).
- **Shizuku 13 or later**, running and authorized for AppOpsNext.
- The bundled native backend targets **ARM64**. Other CPU architectures have
  not been verified.

Root is not required when Shizuku is started through ADB or wireless debugging.
AppOpsNext still needs Shizuku for privileged operations.

1. Install and start [Shizuku](https://shizuku.rikka.app/).
2. Download `app-release.apk` from the
   [latest release](https://github.com/1zumiii/AppOpsNext/releases/latest) and install it.
3. Open AppOpsNext and grant access when Shizuku prompts you.
4. Choose an app, select an operation, and review the confirmation before applying a change.

Use **Templates** for reusable rules and **History** to review system records.
Automatic template application for newly installed apps is optional; configure
its rules before enabling it. The first reconciliation establishes an existing-app
baseline; it does not retroactively apply the template to every installed app.

For updates, install the new release APK over the existing app to retain its
settings. If Shizuku stops after a reboot or authorization is revoked, restore
the connection before making new privileged reads or changes. Saved history
remains viewable without that connection.

### Tested environments

| Device | System | Validation |
| --- | --- | --- |
| ASUS AI2302 | Android 15 / API 35 | Primary development and physical-device test environment. |
| Xiaomi 24117RN76G | HyperOS 3 / Android 16 / API 36 | Independent user verification of the native backend, reads/writes, history, and camera enforcement. |

These results describe the tested environments, not compatibility with every
OEM ROM. See [backend compatibility notes](docs/PRIVILEGED_BACKENDS.md) for the
evidence and known limitations.

## How history works

History comes from records retained by Android. AppOpsNext prefers individual
access records and falls back to time-bucketed system statistics where needed,
such as for clipboard access. Retention and timestamp precision depend on the
device; this is not a complete, independently recorded audit log.

- Each operation's last successful result is saved locally and restored when
  the app reopens, with its update time shown on the page.
- Returning within **five minutes** reuses fresh results. Older or missing
  results are read when the history screen is visible, the app is in the
  foreground, and the backend is connected; periodic refresh follows the same conditions.
- Manual refresh bypasses the freshness check. Results update as each operation
  finishes, and a failed read keeps the previous result with an error message.
- Before the first successful read, the page distinguishes unloaded history
  from a genuine zero count.

The saved result is a cache of the latest successful read, not a permanent
archive. It is stored in the app's private storage and excluded from Android
backup; clearing app data or uninstalling removes it.

## What an AppOps change means

**AppOps and Android runtime permissions are separate layers.** Setting an
AppOp to Allow cannot grant a missing runtime permission. Android or an OEM
policy may also normalize or reject a requested mode. AppOpsNext explains
runtime-permission failures and provides a route to the app's system settings.

Changes use a verified transaction:

```text
Read and check the original state
  → Write the requested mode
  → Read back and verify
  → On failure, attempt to restore and verify the original state
```

Manual, batch, and automatic-template writes share a serialized transaction
queue. A command completing is not sufficient to report success; verification
and restoration outcomes are surfaced separately. Restoration is attempted,
not guaranteed.

UID-scoped changes can affect multiple apps sharing that UID. The confirmation
screen identifies those packages. Automatic scope fallback is constrained to
avoid silently extending a change to other apps. Batch results report each
target separately.

## Troubleshooting

- **Cannot connect:** confirm Shizuku is running and AppOpsNext is authorized,
  then check **Settings → Connection and diagnostics**. The app tries its
  bundled native backend first and Shizuku UserService if native startup fails.
- **A mode will not apply:** check the Android runtime permission and the
  reported verification result. Some system restrictions cannot be overridden
  through AppOps.
- **History looks old or incomplete:** check the saved update time, refresh
  manually, and verify the connection. Android controls which records exist.
- **Reporting a problem:** include the device, Android/ROM version, reproduction
  steps, and a diagnostic report from Settings. Review and redact that report
  before posting it in a public issue.

## Build from source

Use **JDK 17**, **Go 1.24+**, and the **Android SDK with platform 36** installed.
The repository includes the Gradle wrapper. Set `JAVA_HOME` to JDK 17 and point
`local.properties` (`sdk.dir`) or `ANDROID_HOME` to your SDK installation.

```shell
# Optional: set this if Go is not on PATH.
# export GO_EXECUTABLE="/absolute/path/to/go"

./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.
Gradle also cross-compiles the bundled daemon for Android ARM64; a separate NDK
build is not required. Debug builds keep the screen awake while the app is in
the foreground and use a different signing identity from public releases.

Run the same checks as [Android CI](.github/workflows/ci.yml):

```shell
(cd daemon && "${GO_EXECUTABLE:-go}" test ./...)
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon
```

### Signed release builds

Public release signing material is excluded from Git. The configured release
build expects `.signing/appopsnext-release.keystore`, alias `appopsnext`, and
`APPOPSNEXT_STORE_PASSWORD` / `APPOPSNEXT_KEY_PASSWORD` in the environment:

```shell
./gradlew :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`.
For your own distribution, supply your own keystore. Updating an existing
installation requires the same signing identity. Maintainer validation and
publishing steps are in the [release checklist](docs/RELEASE.md).

## Code and documentation

Android packages live under `app/src/main/java/dev/izumi/appopsnext/`.

| Location | Responsibility |
| --- | --- |
| `presentation/` | Compose screens, ViewModels, and UI state. |
| `appops/` | Commands, parsers, scope handling, and verified write transactions. |
| `nativebackend/`, `shizuku/` | Privileged connections, native pipes, and UserService fallback. |
| `apps/`, `settings/` | App discovery, metadata caching, and preferences. |
| `templates/`, `newapps/` | Template persistence, installation detection, and resumable rule execution. |
| `history/` | System-history parsing, refresh scheduling, and local snapshots. |
| `diagnostics/` | Environment and connection reports. |
| [`daemon/`](daemon/) at the repository root | Go daemon with an allowlisted command protocol. |

Further reading: [Architecture](docs/ARCHITECTURE.md) ·
[Privileged backends](docs/PRIVILEGED_BACKENDS.md) ·
[Device findings](docs/DEVICE_FINDINGS.md) ·
[Release checklist](docs/RELEASE.md).

## Project background

AppOpsNext is an independent clean-room implementation inspired by the general
product idea and workflows of the historical App Ops application (`rikka.appops`).
It is not a fork, port, modified build, or official successor.

It includes no legacy application source, decompiled code, assets, branding,
or configuration data, and does not require or provide migration from that app.
AppOpsNext is not developed, endorsed, or supported by RikkaApps or the original
App Ops author. Its use of Shizuku does not imply affiliation with its maintainers.
“AppOps” refers to Android's built-in system service.
