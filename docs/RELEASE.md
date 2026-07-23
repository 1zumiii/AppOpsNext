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
   ./gradlew :app:testDebugUnitTest :app:lintDebug \
     :app:assembleDebug :app:assembleRelease
   ```

3. Verify the release APK signature:

   ```shell
   "$ANDROID_HOME/build-tools/36.0.0/apksigner" verify \
     --verbose \
     --print-certs \
     app/build/outputs/apk/release/app-release.apk
   ```

4. Install the exact verified APK on the reference device and repeat the
   manual smoke test.
5. Commit and push the release source, create an annotated `v<version>` tag,
   and attach the same APK to the GitHub Release.
6. Record a SHA-256 checksum in the release notes.
