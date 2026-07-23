# Android 15 device findings

These notes record observed behavior on the ASUS AI2302 running Android 15
(API 35). They are compatibility evidence, not assumptions about every ROM.

## Shell operation names

The AppOps shell service accepts named operations and modes:

```text
cmd appops get <PACKAGE> <OP>
cmd appops set <PACKAGE> <OP> <MODE>
```

The implementation stores the stable Android operation string, such as
`android:run_in_background`, and keeps shell presentation names, such as
`RUN_IN_BACKGROUND`, at the command adapter boundary. Numeric operation codes
are never persisted.

## Runtime-permission coupling

`CAMERA` was not suitable for the first package-mode round-trip test. After the
test target received the camera runtime permission, Android reported a UID mode
of `foreground`, and package writes were normalized back to `allow`. This is
consistent with runtime permissions and AppOps being coupled for protected
APIs, but the exact normalization timing is device-specific.

The first deterministic write proof therefore uses `RUN_IN_BACKGROUND`, which
was verified on this device as:

```text
No operations. / default
RUN_IN_BACKGROUND: ignore
RUN_IN_BACKGROUND: default
```

Camera, microphone, and location must later be tested through their real APIs
and evaluated with both runtime-permission state and UID/package AppOps state.

## Primary references

- [Android AppOpsManager API](https://developer.android.com/reference/android/app/AppOpsManager)
- [AOSP AppOpsService shell commands](https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/appop/AppOpsService.java)
