package dev.izumi.appops.shizuku;

interface IPrivilegedAppOpsService {
    int getUid() = 1;
    int getPid() = 2;
    int getApiLevel() = 3;
    void destroy() = 16777114;
}

