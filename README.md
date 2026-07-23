# AppOps Next

A modern AppOps manager for Android 15+, powered by Shizuku.

The first supported device is an ASUS AI2302 running Android 15 (API 35).
The project intentionally uses only clean-room implementations based on public
Android and Shizuku behavior.

## Development

- JDK 17
- Android SDK
- A physical Android 15 device with USB debugging
- Shizuku 13+

Build the debug APK:

```shell
./gradlew :app:assembleDebug
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for package boundaries and
maintenance rules.

