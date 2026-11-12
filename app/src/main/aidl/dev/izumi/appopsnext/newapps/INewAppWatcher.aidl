package dev.izumi.appopsnext.newapps;

interface INewAppWatcher {
    /** Shizuku's transaction for stopping a user service. */
    void destroy() = 16777114;

    /** Starts watching installs of [userId] and returns the watcher's PID. */
    int watch(int userId) = 1;

    /**
     * What the watcher found when it started watching, as encoded by
     * WatcherStartReport, or null before the first watch. A watcher built
     * before this method existed does not answer it.
     */
    String startReport() = 2;
}
