# AppOps Next

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
- per-app read-only AppOps details with package/UID scope and recorded metadata
