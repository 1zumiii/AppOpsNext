package dev.izumi.appops.shizuku;

import dev.izumi.appops.appops.model.ShellCommandResult;

interface IPrivilegedAppOpsService {
    int getUid() = 1;
    int getPid() = 2;
    int getApiLevel() = 3;
    ShellCommandResult getPackageOps(String packageName) = 4;
    void destroy() = 16777114;
}
