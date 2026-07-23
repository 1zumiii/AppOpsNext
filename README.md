# AppOpsNext

A modern AppOps manager for Android 15+, powered by Shizuku.

The first supported device is an ASUS AI2302 running Android 15 (API 35).
The project intentionally uses only clean-room implementations based on public
Android and Shizuku behavior.

It does not connect to or depend on the legacy `rikka.appops` application.
“System AppOps” in the UI refers to Android's built-in AppOps system service.

## Development

- JDK 17
- Android SDK
- A physical Android 15 device with USB debugging
- Shizuku 13+

Build the debug APK:

```shell
./gradlew :app:assembleDebug :test-target:assembleDebug
```

Debug builds keep the screen awake while the app is in the foreground to
support long-running physical-device tests. Release builds do not change the
system screen timeout.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for package boundaries and
maintenance rules and [docs/DEVICE_FINDINGS.md](docs/DEVICE_FINDINGS.md) for
behavior verified on the first supported device.

## Verified milestone

- Shizuku permission lifecycle
- shell UID 2000 UserService over AIDL
- automatic UserService reconnection after the frontend process restarts
- typed system AppOps reads through the privileged backend
- isolated command construction and unit-tested output parsing
- 41 AppOps entries read from the development app on the Android 15 test device
- isolated `test-target` APK for write verification
- verified `default -> ignore -> default` mode restoration for
  `android:run_in_background`
- complete current-user application discovery with system/user and UID metadata
- app-name and package-name search across 455 apps on the Android 15 test device
- per-app AppOps details with package/UID scope and recorded metadata
- confirmed package-scope mode editing with stale-state detection, independent
  read-back verification, and automatic restoration after a failed change
- physical-device UI verification of `default -> ignore -> default` for
  `RUN_IN_BACKGROUND`
- persistent setting to hide system applications
- common-first AppOp sorting and Chinese/English/raw-name search
- localized permission titles with the raw system operation on a separate,
  lower-emphasis line
- package/UID scope resolution and shared-UID impact confirmation
- physical-device UI verification of `foreground -> ignore -> foreground` for
  ChatGPT's UID-scoped `CAMERA` operation
- two-snapshot package/UID scope loading with validated compatibility fallback
- app-aware skeleton loading and per-operation write progress
- compact relative usage times using one unit (`13 天前`, `23 小时前`,
  `59 分前`, or `少于 1 分钟`)
- verified writes update only the affected row; only failures show a detailed
  result card
- the temporary in-app write-test card has been removed now that the production
  permission editor covers its verification and restoration path
- vector navigation symbols and an adaptive AppOpsNext shield/sliders icon
