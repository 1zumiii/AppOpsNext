package dev.izumi.appops.shizuku;

import dev.izumi.appops.appops.model.ShellCommandResult;

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
    void destroy() = 16777114;
}
