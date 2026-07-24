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
    ShellCommandResult getDiscreteHistory(String operationName) = 9;
    void destroy() = 16777114;
}
