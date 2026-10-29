package dev.izumi.appopsnext.newapps

import android.content.Context
import android.content.pm.PackageInstaller
import android.os.Handler
import android.os.HandlerThread
import android.system.Os
import android.util.Log
import androidx.annotation.Keep
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
        if (watchedUserId == null) installer.registerSessionCallback(callback, handler)
        watchedUserId = userId
        return Os.getpid()
    }

    override fun destroy() {
        runCatching { context?.packageManager?.packageInstaller?.unregisterSessionCallback(callback) }
        exitProcess(0)
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

    private companion object {
        const val TAG = "NewAppWatcher"
        const val FLAG_INCLUDE_STOPPED_PACKAGES = 0x20
        const val BROADCAST_TIMEOUT_SECONDS = 10L
    }
}
