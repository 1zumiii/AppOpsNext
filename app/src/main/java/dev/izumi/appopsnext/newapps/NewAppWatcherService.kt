package dev.izumi.appopsnext.newapps

import android.content.Context
import android.content.pm.PackageInstaller
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Log
import androidx.annotation.Keep
import dev.izumi.appopsnext.diagnostics.ProcessStats
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Runs in a Shizuku daemon process, which outlives the app process. It watches
 * install sessions through the public [PackageInstaller] callback and wakes the
 * app with an explicit broadcast when one succeeds. The app then looks for new
 * apps itself, so this process knows nothing about templates.
 */
class NewAppWatcherService : INewAppWatcher.Stub {
    private val context: Context?
    private val thread = HandlerThread("new-app-watcher").apply { start() }
    private val handler = Handler(thread.looper)
    private var watchedUserId: Int? = null

    @Volatile
    private var startReport: String? = null

    private val callback = object : PackageInstaller.SessionCallback() {
        override fun onCreated(sessionId: Int) = Unit
        override fun onBadgingChanged(sessionId: Int) = Unit
        override fun onActiveChanged(sessionId: Int, active: Boolean) = Unit
        override fun onProgressChanged(sessionId: Int, progress: Float) = Unit

        override fun onFinished(sessionId: Int, success: Boolean) {
            if (success) watchedUserId?.let(::wakeApp)
        }
    }

    constructor() : super() {
        context = null
    }

    @Keep
    constructor(context: Context) : super() {
        this.context = context
    }

    @Synchronized
    override fun watch(userId: Int): Int {
        val installer = context?.packageManager?.packageInstaller
            ?: throw IllegalStateException("Shizuku did not pass a context")
        if (watchedUserId == null) {
            // Only the watcher the app is connected to gets here, so it is the
            // current one and any other is left over.
            val stale = endStaleWatchers()
            installer.registerSessionCallback(callback, handler)
            startReport = WatcherStartReport.encode(
                WatcherStartReport(
                    pid = Os.getpid(),
                    uid = Os.getuid(),
                    startEpochMillis = runCatching { startOf(File("/proc/self/stat")) }.getOrNull(),
                    stale = stale.getOrNull(),
                    scanError = stale.exceptionOrNull()?.let { "${it::class.java.simpleName}:${it.message}" },
                ),
            )
        }
        watchedUserId = userId
        return Os.getpid()
    }

    override fun startReport(): String? = startReport

    override fun destroy() {
        runCatching { context?.packageManager?.packageInstaller?.unregisterSessionCallback(callback) }
        exitProcess(0)
    }

    private fun endStaleWatchers(): Result<List<WatcherStartReport.StaleWatcher>> {
        val packageName = context?.packageName
            ?: return Result.failure(IllegalStateException("no package name"))
        return runCatching {
            val ownUid = Os.getuid()
            StaleWatcherProcesses.find(
                procDir = File("/proc"),
                processName = "$packageName:$PROCESS_SUFFIX",
                ownPid = Os.getpid(),
                ownedBy = { Os.stat(it.path).st_uid == ownUid },
            ).map { pid ->
                // Read before the kill, which removes the process's entry.
                val started = runCatching { startOf(File("/proc/$pid/stat")) }.getOrNull()
                Log.i(TAG, "Ending a stale watcher. pid=$pid")
                val killError = try {
                    Os.kill(pid, OsConstants.SIGKILL)
                    null
                } catch (error: ErrnoException) {
                    OsConstants.errnoName(error.errno) ?: error.errno.toString()
                }
                WatcherStartReport.StaleWatcher(pid, started, killError)
            }
        }.onFailure { Log.w(TAG, "Unable to look for stale watchers", it) }
    }

    /** The wall time a process started, from its `stat` file. */
    private fun startOf(stat: File): Long? {
        val ticks = ProcessStats.parse(stat.readText())?.startTicks ?: return null
        return ProcessStats.startEpochMillis(
            startTicks = ticks,
            clockTicksPerSecond = Os.sysconf(OsConstants._SC_CLK_TCK),
            bootEpochMillis = System.currentTimeMillis() - SystemClock.elapsedRealtime(),
        )
    }

    private fun wakeApp(userId: Int) {
        val packageName = context?.packageName ?: return
        runCatching {
            val process = ProcessBuilder(
                "/system/bin/am", "broadcast",
                "--user", userId.toString(),
                // The app may not have run since it was installed or stopped.
                "-f", FLAG_INCLUDE_STOPPED_PACKAGES.toString(),
                "-a", NewAppInstallReceiver.ACTION_INSTALL_FINISHED,
                "-n", "$packageName/${NewAppInstallReceiver::class.java.name}",
            ).redirectErrorStream(true).start()
            process.inputStream.use { it.readBytes() }
            if (!process.waitFor(BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) process.destroy()
        }.onFailure { Log.w(TAG, "Unable to wake the app", it) }
    }

    companion object {
        /** Follows the package name in the name of the watcher's process. */
        const val PROCESS_SUFFIX = "watcher"
        private const val TAG = "NewAppWatcher"
        private const val FLAG_INCLUDE_STOPPED_PACKAGES = 0x20
        private const val BROADCAST_TIMEOUT_SECONDS = 10L
    }
}
