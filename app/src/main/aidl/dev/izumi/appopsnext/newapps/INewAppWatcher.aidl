package dev.izumi.appopsnext.newapps;

interface INewAppWatcher {
    /** Shizuku's transaction for stopping a user service. */
    void destroy() = 16777114;

    /** Starts watching installs of [userId] and returns the watcher's PID. */
    int watch(int userId) = 1;
}
