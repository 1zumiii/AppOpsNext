# Android 15 device findings

These notes record observed behavior on the ASUS AI2302 running Android 15
(API 35). They are compatibility evidence, not assumptions about every ROM.

## Shell operation names

The AppOps shell service accepts named operations and modes:

```text
cmd appops get <PACKAGE> <OP>
cmd appops set <PACKAGE> <OP> <MODE>
cmd appops set --uid <PACKAGE> <OP> <MODE>
```

The implementation stores the stable Android operation string, such as
`android:run_in_background`, and keeps shell presentation names, such as
`RUN_IN_BACKGROUND`, at the command adapter boundary. Numeric operation codes
are never persisted.

## Runtime-permission coupling

`CAMERA` was not suitable for the first package-mode round-trip test. After the
disposable fixture app received the camera runtime permission, Android reported
a UID mode of `foreground`, and package writes were normalized back to `allow`.
This is consistent with runtime permissions and AppOps being coupled for
protected APIs, but the exact normalization timing is device-specific.

The first deterministic write proof therefore uses `RUN_IN_BACKGROUND`, which
was verified on this device as:

```text
No operations. / default
RUN_IN_BACKGROUND: ignore
RUN_IN_BACKGROUND: default
```

The production package-mode editor was also exercised end to end through its
Compose UI. The confirmation flow persisted `default -> ignore`, an independent
ADB read returned `RUN_IN_BACKGROUND: ignore`, and a second confirmed edit
restored `ignore -> default`. The final independent read returned
`RUN_IN_BACKGROUND: default`.

Camera, microphone, and location must be evaluated with both
runtime-permission state and UID/package AppOps state.

## UID block parsing

On this ROM, the full-package output prefixes only the first line of a
multi-line UID block:

```text
Uid mode: COARSE_LOCATION: ignore
FINE_LOCATION: ignore
CAMERA: ignore
```

Those later lines are still UID-scoped even though they have no repeated
prefix. Actual package-scoped entries can appear later with the same operation
names. Treating every unprefixed row as package-scoped caused AppOpsNext to
write the wrong scope and then correctly report a read-back mismatch.

A numeric UID read returns only the UID block on this device. AppOpsNext now
uses one package read and one UID read, verifies their common prefix, and then
splits the scopes. The normal ChatGPT detail load was complete by the
one-second device capture instead of taking four to five seconds. The previous
single-operation path remains as a compatibility fallback.

## Verified UID write

ChatGPT had a granted camera runtime permission and reported:

```text
Uid mode: CAMERA: foreground
CAMERA: allow
```

Through the AppOpsNext Compose UI, its effective UID mode was changed from
`foreground` to `ignore`. An independent ADB read returned `CAMERA: ignore`.
The same UI then restored it to `foreground`, and a final independent read
confirmed that state. The package-scoped `CAMERA: allow` entry was not changed.
Both changes completed without reloading the full detail snapshot; only the
camera row showed progress and its verified mode was updated locally.

On this device, the same effective camera row can be switched reliably between
`foreground` and `ignore`. Requests for `allow` or `default` are not retained by
the system and therefore fail AppOpsNext's independent read-back verification.
That behavior is treated as a device/runtime-permission constraint rather than
as a successful write.

By contrast, attempting to promote a UID operation whose runtime permission is
denied can be normalized or rejected by Android. AppOps can further restrict a
granted capability, but it cannot grant a denied runtime permission. The UI now
explains this case and the transaction restores the original AppOps mode.

## Primary references

- [Android AppOpsManager API](https://developer.android.com/reference/android/app/AppOpsManager)
- [AOSP AppOpsService shell commands](https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/appop/AppOpsService.java)
