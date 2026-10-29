package dev.izumi.appopsnext.shizuku;

import dev.izumi.appopsnext.appops.model.ShellCommandResult;

interface IPrivilegedAppOpsService {
    int getUid() = 1;
    int getPid() = 2;
    int getApiLevel() = 3;
    ShellCommandResult getPackageOps(String packageName) = 4;
    ShellCommandResult getPackageOp(String packageName, String operationName) = 5;
    ShellCommandResult setPackageOpMode(
        String packageName,
        String operationName,
        String mode
    ) = 6;
    ShellCommandResult setUidOpMode(
        String packageName,
        String operationName,
        String mode
    ) = 7;
    ShellCommandResult getUidOps(int uid) = 8;
    ShellCommandResult getHistory(String operationName) = 9;
    ShellCommandResult getUidStates(String packageName) = 10;
    ShellCommandResult getWatchers() = 11;
    ShellCommandResult getPackagePermissions(String packageName) = 12;
    ShellCommandResult setRevokedCompat(
        int userId,
        String packageName,
        String permissionName,
        boolean revoked
    ) = 13;
    ShellCommandResult getOpStates(String operationName) = 14;
    ShellCommandResult listUsers() = 15;
    ShellCommandResult listPackagesForUser(int userId) = 16;
    ShellCommandResult getPackageOpsForUser(int userId, String packageName) = 17;
    ShellCommandResult getPackageOpForUser(
        int userId,
        String packageName,
        String operationName
    ) = 18;
    ShellCommandResult setPackageOpModeForUser(
        int userId,
        String packageName,
        String operationName,
        String mode
    ) = 19;
    ShellCommandResult setUidOpModeForUser(
        int userId,
        String packageName,
        String operationName,
        String mode
    ) = 20;
    /** Only the time lines of `dumpsys package packages`, which in full exceeds a binder transaction. */
    ShellCommandResult getPackageTimes() = 21;
    void destroy() = 16777114;
}
