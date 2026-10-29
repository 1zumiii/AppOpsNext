package dev.izumi.appopsnext.newapps

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Process
import dev.izumi.appopsnext.BuildConfig
import dev.izumi.appopsnext.appops.UserUids
import dev.izumi.appopsnext.diagnostics.DiagnosticLogRepository
import rikka.shizuku.Shizuku

/**
 * Keeps [NewAppWatcherService] running in a Shizuku daemon process while it is
 * wanted. The daemon outlives the app process but not Shizuku. When Shizuku starts
 * again, after a reboot for example, it hands its binder to the app, which starts
 * the app process if needed, and the listener here binds the watcher again.
 */
class NewAppWatcherClient(
    context: Context,
    private val diagnosticLog: DiagnosticLogRepository,
) {
    private val args = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, NewAppWatcherService::class.java.name),
    )
        .daemon(true)
        .processNameSuffix(PROCESS_SUFFIX)
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    @Volatile
    private var wanted = false

    @Volatile
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder?) {
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
            bound = false
            diagnosticLog.warning(LOG_SOURCE, "Install watcher disconnected.")
        }
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener { bindIfWanted() }
    private val binderDead = Shizuku.OnBinderDeadListener { bound = false }

    init {
        // Sticky, so a binder that arrived before this point still counts.
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
    }

    fun setWanted(value: Boolean) {
        wanted = value
        if (value) bindIfWanted() else stop()
    }

    @Synchronized
    private fun bindIfWanted() {
        if (!wanted || bound || !shizukuReady()) return
        runCatching { Shizuku.bindUserService(args, connection) }
            .onSuccess { bound = true }
            .onFailure { error -> diagnosticLog.error(LOG_SOURCE, "Unable to bind the install watcher.", error) }
    }

    /** Ends the daemon too, which an unbind alone would leave running. */
    @Synchronized
    private fun stop() {
        if (!shizukuReady()) return
        runCatching { Shizuku.unbindUserService(args, connection, true) }
            .onSuccess {
                if (bound) diagnosticLog.info(LOG_SOURCE, "Install watcher stopped.")
                bound = false
            }
    }

    private fun shizukuReady(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    private companion object {
        const val LOG_SOURCE = "NewAppWatcher"
        const val PROCESS_SUFFIX = "watcher"
    }
}
