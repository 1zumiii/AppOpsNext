package dev.izumi.appopsnext.newapps

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import dev.izumi.appopsnext.BuildConfig
import dev.izumi.appopsnext.appops.UserUids
import dev.izumi.appopsnext.diagnostics.DiagnosticLogRepository
import rikka.shizuku.Shizuku

/**
 * Keeps [NewAppWatcherService] running in a Shizuku daemon process while it is
 * wanted. The daemon outlives the app process but not Shizuku. When Shizuku starts
 * again, after a reboot for example, it hands its binder to the app, which starts
 * the app process if needed, and the listener here binds the watcher again.
 *
 * The system can also end the daemon on its own. It is then started again after
 * a pause, a limited number of times, as [WatcherRebindBackoff] decides.
 */
class NewAppWatcherClient(
    context: Context,
    private val diagnosticLog: DiagnosticLogRepository,
) {
    private val args = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, NewAppWatcherService::class.java.name),
    )
        .daemon(true)
        .processNameSuffix(NewAppWatcherService.PROCESS_SUFFIX)
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val handler = Handler(Looper.getMainLooper())
    private val backoff = WatcherRebindBackoff()

    // Guarded by this.
    private var wanted = false
    private var bound = false
    private var connected = false

    private val rebind = Runnable { bindIfWanted() }

    /** Shizuku drops a service that does not start in time without saying so. */
    private val connectTimeout = Runnable {
        synchronized(this) {
            if (!bound || connected) return@Runnable
            bound = false
            diagnosticLog.warning(LOG_SOURCE, "Install watcher did not connect in time.")
            scheduleRebind()
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder?) {
            synchronized(this@NewAppWatcherClient) {
                connected = true
                handler.removeCallbacks(connectTimeout)
                backoff.onConnected(SystemClock.elapsedRealtime())
            }
            val result = runCatching {
                INewAppWatcher.Stub.asInterface(binder ?: error("empty binder"))
                    .watch(UserUids.userIdOf(Process.myUid()))
            }
            result.onSuccess { pid ->
                diagnosticLog.info(LOG_SOURCE, "Install watcher running. pid=$pid")
            }.onFailure { error ->
                diagnosticLog.error(LOG_SOURCE, "Install watcher did not start.", error)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            synchronized(this@NewAppWatcherClient) {
                bound = false
                connected = false
                diagnosticLog.warning(LOG_SOURCE, "Install watcher disconnected.")
                scheduleRebind()
            }
        }
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener {
        // A new Shizuku has no watcher yet, whatever happened under the last one.
        synchronized(this) { backoff.reset() }
        bindIfWanted()
    }
    private val binderDead = Shizuku.OnBinderDeadListener {
        synchronized(this) {
            bound = false
            connected = false
            cancelPending()
        }
    }

    init {
        // Sticky, so a binder that arrived before this point still counts.
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
    }

    @Synchronized
    fun setWanted(value: Boolean) {
        wanted = value
        if (value) bindIfWanted() else stop()
    }

    @Synchronized
    private fun bindIfWanted() {
        if (!wanted || bound || !shizukuReady()) return
        handler.removeCallbacks(rebind)
        runCatching { Shizuku.bindUserService(args, connection) }
            .onSuccess {
                bound = true
                handler.postDelayed(connectTimeout, CONNECT_TIMEOUT_MILLIS)
            }
            .onFailure { error -> diagnosticLog.error(LOG_SOURCE, "Unable to bind the install watcher.", error) }
    }

    /** Ends the daemon too, which an unbind alone would leave running. */
    @Synchronized
    private fun stop() {
        cancelPending()
        backoff.reset()
        if (!shizukuReady()) return
        runCatching { Shizuku.unbindUserService(args, connection, true) }
            .onSuccess {
                if (bound) diagnosticLog.info(LOG_SOURCE, "Install watcher stopped.")
                bound = false
                connected = false
            }
    }

    private fun scheduleRebind() {
        if (!wanted) return
        val delayMillis = backoff.nextDelayMillis(SystemClock.elapsedRealtime())
        if (delayMillis == null) {
            diagnosticLog.warning(
                LOG_SOURCE,
                "Install watcher keeps stopping. It starts again with the app or Shizuku.",
            )
            return
        }
        handler.removeCallbacks(rebind)
        handler.postDelayed(rebind, delayMillis)
    }

    private fun cancelPending() {
        handler.removeCallbacks(rebind)
        handler.removeCallbacks(connectTimeout)
    }

    private fun shizukuReady(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    private companion object {
        const val LOG_SOURCE = "NewAppWatcher"

        /** Longer than the 30 seconds Shizuku itself waits for a service to start. */
        const val CONNECT_TIMEOUT_MILLIS = 45_000L
    }
}
