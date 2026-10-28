#!/usr/bin/env bash
# Captures the AppOps text output that AppOpsNext parses, from a device or
# emulator reachable through adb, for DeviceDumpCompatibilityTest.
#
# Usage: scripts/capture-device-dumps.sh <output-dir>
#
# The read commands mirror daemon/cmd/appopsnextd/server.go. Before reading,
# the script makes a few states non-default so the parsers have entries to
# find, and restores them on exit. Set ANDROID_SERIAL to pick a device and
# APPOPSNEXT_DUMP_PACKAGE to pick the target package.
#
# Writes use RUN_IN_BACKGROUND (package mode) and RUN_ANY_IN_BACKGROUND (UID
# mode) because Android 16 QPR2 and later block AppOps writes to
# runtime-permission ops. CAMERA is reached through its permission flag instead.
set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "usage: $0 <output-dir>" >&2
    exit 2
fi
out=$1
mkdir -p "$out"
out=$(cd "$out" && pwd)

readonly USER_ID=0
readonly PERMISSION=android.permission.CAMERA
readonly PACKAGE_OP=RUN_IN_BACKGROUND
readonly UID_OP=RUN_ANY_IN_BACKGROUND
readonly HISTORY_OP=CAMERA

log() { echo "$*" | tee -a "$out/setup.log" >&2; }

# Setup steps are logged, not fatal: the test reports what is missing, and the
# dumps stay useful for inspection.
setup() {
    log "\$ $*"
    adb shell "$@" >>"$out/setup.log" 2>&1 || log "  exit $?"
}

adb wait-for-device
until [[ $(adb shell getprop sys.boot_completed | tr -d '\r') == 1 ]]; do sleep 2; done

# A regular app UID that requests CAMERA. A shared system UID such as Settings
# (1000) would carry the UID-mode write to system_server.
pick_package() {
    local candidate
    for candidate in ${APPOPSNEXT_DUMP_PACKAGE:-} com.android.camera2 com.android.chrome com.google.android.GoogleCamera; do
        # grep -q would stop reading early and fail the pipe under pipefail.
        if adb shell dumpsys package "$candidate" 2>/dev/null | grep -F "$PERMISSION" >/dev/null; then
            echo "$candidate"
            return
        fi
    done
    return 1
}
package=$(pick_package) || { log "no candidate package requests $PERMISSION"; exit 1; }
uid=$(adb shell cmd package list packages -U --user "$USER_ID" "$package" | tr -d '\r' |
    awk -v name="package:$package" '$1 == name && $2 ~ /^uid:[0-9]+$/ { print substr($2, 5); exit }')
if [[ -z $uid ]]; then
    log "cannot read the UID of $package"
    exit 1
fi
log "package=$package uid=$uid"

restore() {
    setup cmd appops set "$package" "$PACKAGE_OP" default
    setup cmd appops set --uid "$package" "$UID_OP" default
    setup cmd package clear-permission-flags --user "$USER_ID" "$package" "$PERMISSION" revoked-compat
}
trap restore EXIT

# History first: the permission flag below turns CAMERA off for this UID.
# CAMERA records individual accesses, and a granted permission with the app on
# screen lets the shell's start command through.
setup pm grant "$package" "$PERMISSION"
setup monkey -p "$package" -c android.intent.category.LAUNCHER 1
sleep 3
setup cmd appops start "$package" "$HISTORY_OP"
sleep 1
setup cmd appops stop "$package" "$HISTORY_OP"

setup cmd appops set "$package" "$PACKAGE_OP" ignore
setup cmd appops set --uid "$package" "$UID_OP" ignore
setup cmd package set-permission-flags --user "$USER_ID" "$package" "$PERMISSION" revoked-compat

printf 'name\texit\tcommand\n' >"$out/commands.tsv"
# Saves stdout as <name>.txt, stderr as <name>.stderr when not empty, and the
# exit status in commands.tsv.
capture() {
    local name=$1 status=0
    shift
    adb shell "$@" >"$out/$name.txt" 2>"$out/$name.stderr" || status=$?
    [[ -s $out/$name.stderr ]] || rm -f "$out/$name.stderr"
    printf '%s\t%s\t%s\n' "$name" "$status" "$*" >>"$out/commands.tsv"
}

# GET_PACKAGE_OPS, GET_PACKAGE_OP, GET_UID_OPS
capture package-ops cmd appops get "$package"
capture package-op-camera cmd appops get "$package" CAMERA
capture package-op-package-mode cmd appops get "$package" "$PACKAGE_OP"
capture uid-ops cmd appops get "$uid"
# GET_OP_STATES
capture op-states-camera dumpsys appops --op CAMERA
capture op-states-package-mode dumpsys appops --op "$PACKAGE_OP"
capture op-states-uid-mode dumpsys appops --op "$UID_OP"
# GET_UID_STATES
capture uid-states dumpsys appops --package "$package"
# GET_WATCHERS
capture watchers dumpsys appops --watchers
# GET_HISTORY
capture history-camera dumpsys appops --history --include-discrete 0 --op "$HISTORY_OP"
# GET_PACKAGE_PERMISSIONS
capture package-permissions dumpsys package "$package"

{
    echo "package=$package"
    echo "uid=$uid"
    echo "user=$USER_ID"
    echo "permission=$PERMISSION"
    echo "packageOp=$PACKAGE_OP"
    echo "uidOp=$UID_OP"
    echo "historyOp=$HISTORY_OP"
    echo "sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
    echo "release=$(adb shell getprop ro.build.version.release | tr -d '\r')"
    echo "fingerprint=$(adb shell getprop ro.build.fingerprint | tr -d '\r')"
} >"$out/capture.properties"
log "saved $(($(wc -l <"$out/commands.tsv") - 1)) dumps to $out"
