# AppOpsNext

[English](README.md) | [简体中文](README.zh-CN.md)

A modern, clean-room AppOps manager for Android 15+, powered by
[Shizuku](https://shizuku.rikka.app/).

AppOpsNext reads and changes Android's built-in AppOps state.

> [!IMPORTANT]
> Android 15 (API 35) development and verification use an ASUS AI2302. The
> native backend has also been independently verified on a Xiaomi 24117RN76G
> running HyperOS 3 / Android 16 (API 36). Support outside these tested devices
> and system versions remains unknown.

## Relationship to the legacy App Ops

AppOpsNext is an independent clean-room reimplementation inspired by the
general product idea and workflows of the historical App Ops application
(`rikka.appops`). It is not a fork, port, patched build, or official successor.

- No source code, decompiled code, assets, branding, or configuration data from
  the legacy application is included.
- AppOpsNext does not connect to, migrate from, interoperate with, or require
  the legacy application.
- AppOpsNext is not developed, endorsed, maintained, or supported by RikkaApps
  or the original App Ops author.
- AppOpsNext uses Shizuku as an independently published privilege bridge. That
  technical dependency does not imply affiliation with or endorsement by the
  Shizuku or legacy App Ops maintainers.

Names of third-party projects are used only to explain compatibility and
project history. “AppOps” in this project's name refers to Android's built-in
AppOps system service, not an interface or technology owned by the legacy app.

## Features

- Browse current-user applications with package, UID, and system-app metadata
- Hide system applications by default, with a persistent Settings toggle
- Search applications and permissions by localized or raw system names
- Read package-scoped and UID-scoped AppOps independently
- Change AppOps modes with stale-state checks and independent read-back
- Restore the original mode automatically when a write cannot be verified
- Show per-operation progress without reloading the complete detail screen
- Display compact, localized recent-use timestamps
- Review camera, microphone, and location system history with summary charts,
  per-permission statistics, and app-linked timelines
- Add or remove monitored AppOps and refresh history automatically while the
  privileged connection remains available
- Create reusable permission templates with editable modes and automatic
  AppOps scope fallback
- Add, remove, and long-press drag template rules into a persistent custom order
- Apply one template to an app or batch-apply it to multiple applications
- Configure a protected default template for newly installed apps, with
  optional automatic application, catch-up detection, and result notifications
- Change several permissions in one app as a verified batch operation
- Report every batch success and failure in a persistent result dialog
- Switch between system language, Simplified Chinese, and English
- Run AppOps commands through a bundled native daemon, with Shizuku UserService
  retained as an automatic fallback

## Requirements

- Android 15 (API 35) or newer
- Shizuku 13 or newer
- ADB or wireless debugging to start Shizuku on a non-rooted device

Shizuku starts AppOpsNext's bundled native daemon with the shell identity. The
app then communicates with that daemon through private process pipes, avoiding
the UserService callback path that can fail on some Android 16 / HyperOS
devices. If native startup fails, AppOpsNext automatically tries its Shizuku
UserService backend. If Shizuku stops after a reboot or authorization is
revoked, privileged reads and writes remain unavailable until it is restored.

## Installation

1. Install and start Shizuku.
2. Download the latest APK from
   [GitHub Releases](https://github.com/1zumiii/AppOpsNext/releases).
3. Install the APK and grant AppOpsNext access when Shizuku asks.
4. Open an application and confirm every requested change before applying it.

Android runtime permissions and AppOps are separate layers. AppOps can further
restrict an already granted capability, but it cannot grant a runtime
permission denied by Android or an OEM policy. Some modes may therefore be
normalized or rejected by the system; AppOpsNext reports this as a failed
verification instead of claiming success.

## Safety model

Every single or batched write follows the same bounded transaction:

```text
read current value
  -> confirm it has not changed
  -> write the requested typed mode
  -> read back and verify
  -> restore and verify the original value after failure
```

Template writes start with the current app package. If Android rejects that
scope after the original state has been restored, AppOpsNext retries through
the UID only when it belongs exclusively to the target package. Explicit UID
records can still affect several packages sharing one system identity, so the
confirmation UI lists those affected packages. Batch operations run
sequentially and retain a result for every target.

## Development

The project requires JDK 17, Go 1.24 or newer, and an Android SDK. Gradle
cross-compiles the bundled ARM64 daemon with `GOOS=android` and `CGO_ENABLED=0`.
A physical Android 15 device with USB debugging is the primary test
environment. Set `GO_EXECUTABLE` when `go` is not available on `PATH`.

Build the debug app:

```shell
./gradlew :app:assembleDebug
```

Run local verification:

```shell
(cd daemon && go test ./...)
./gradlew :app:testDebugUnitTest :app:lintDebug \
  :app:assembleDebug
```

Debug builds keep the screen awake only while AppOpsNext is in the foreground.
Release builds do not change the system screen timeout.

### Release signing

The release keystore is intentionally excluded from Git. Release builds require
`.signing/appopsnext-release.keystore` and these environment variables:

```shell
export APPOPSNEXT_STORE_PASSWORD="<keystore password>"
export APPOPSNEXT_KEY_PASSWORD="<key password>"
./gradlew :app:assembleRelease
```

Keep an offline backup of the keystore and its passwords. Losing the signing
key makes it impossible to publish updates that install over existing releases.

## Project structure

- `presentation`: Compose screens, state, and reusable UI
- `appops`: command adapters, parsing, repositories, and verified writes
- `nativebackend`: native-daemon bootstrap, private pipe protocol, and gateway
- `shizuku`: authorization, process bootstrap, and UserService fallback
- `daemon`: allowlisted ARM64 shell daemon built with Go
- `apps`: application discovery and pure filtering
- `settings`: typed Preferences DataStore settings
- `templates`: versioned template persistence and ordering
- `newapps`: new-install detection, pending work, automatic policy execution,
  and result notifications
- `history`: discrete AppOps history parsing, repositories, and statistics

See [Architecture](docs/ARCHITECTURE.md) for package boundaries and maintenance
rules, [Privileged backends](docs/PRIVILEGED_BACKENDS.md) for backend selection
and compatibility evidence, and
[Android 15 device findings](docs/DEVICE_FINDINGS.md) for behavior verified on
the reference device. Maintainers should also follow the
[release checklist](docs/RELEASE.md) before publishing an APK.
