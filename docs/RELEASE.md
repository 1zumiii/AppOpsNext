# Release checklist

AppOpsNext uses one dedicated signing identity for every public APK. Never
replace the keystore after publishing a release: Android accepts an update only
when it is signed by the same key as the installed version.

## Local signing material

The local macOS development environment stores:

- keystore: `.signing/appopsnext-release.keystore`
- key alias: `appopsnext`
- password: macOS Keychain service `dev.izumi.appopsnext.release`, account
  `AppOpsNext`

The `.signing` directory is excluded from Git. Keep an encrypted offline backup
of the keystore and the Keychain password.

Load the signing password without printing it:

```shell
set +x
release_password="$(
  security find-generic-password \
    -a AppOpsNext \
    -s dev.izumi.appopsnext.release \
    -w
)"
export APPOPSNEXT_STORE_PASSWORD="$release_password"
export APPOPSNEXT_KEY_PASSWORD="$release_password"
unset release_password
```

## Build and verify

1. Update `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Run the complete local checks:

   ```shell
   export GO_EXECUTABLE=/Users/izumi/Library/Android/sdk/go/1.26.5/bin/go
   export JAVA_HOME=/opt/homebrew/opt/openjdk@17
   (cd daemon && "$GO_EXECUTABLE" test ./...)
   ./gradlew :app:testDebugUnitTest :app:lintDebug \
     :app:assembleDebug :app:assembleRelease --no-daemon
   ```

3. Verify the release APK signature:

   ```shell
   "$ANDROID_HOME/build-tools/36.0.0/apksigner" verify \
     --verbose \
     --print-certs \
     app/build/outputs/apk/release/app-release.apk
   ```

   Expected certificate SHA-256:
   `7816703c5a94356af9124d96e581db4d9e2c007bffd5e5fe259f27e16757a178`.

4. Install the exact verified APK on the reference device with
   `adb install -r --user 0 app/build/outputs/apk/release/app-release.apk`.
   Verify `adb shell pm list packages --user 10 dev.izumi.appopsnext` stays empty.
   After changing the daemon, cold-launch the app and check
   `adb shell ps -A | grep appopsnextd`, plus AppOps reads, a reversible probe
   write, history and monitor registration. Use the separate AppOpsProbe project
   for allowed/denied events; do not uninstall the app or reset its settings.
5. Commit and push the release source, create an annotated `v<version>` tag,
   and attach the same APK to the GitHub Release.
6. Record a SHA-256 checksum in the release notes.

`v1.4.1-beta1` to `v1.4.1-beta3` are published prereleases (version codes 33 to
35), and their tags and assets must not be overwritten. No final 1.4.1 follows
them: `1.5.0` (version code 36) is the next release and includes all of their
changes. The in-app update check treats 1.5.0 as newer than every 1.4.1 beta.
Commit, push and tagging are performed by the maintainer.
