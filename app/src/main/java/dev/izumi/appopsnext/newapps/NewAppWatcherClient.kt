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
import dev.izumi.appopsnext.diagnostics.DiagnosticLogLevel
import dev.izumi.appopsnext.diagnostics.DiagnosticLogRepository
import dev.izumi.appopsnext.diagnostics.DiagnosticLogTier
import java.time.ZoneId
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
    private val logBudget = WatcherLogBudget()

    // Guarded by this.
    private var wanted = false
    private var bound = false
    private var connected = false
    private var bindRequestedAtMillis: Long? = null
    private var connectedAtMillis: Long? = null
    private var pendingRebind: String? = null

    /** Why the current bind was asked for, written with its outcome. */
    private var bindReason: String? = null

    private val rebind = Runnable { bindIfWanted(pendingRebind ?: "rebind") }

    /** Shizuku drops a service that does not start in time without saying so. */
    private val connectTimeout = Runnable {
        synchronized(this) {
            if (!bound || connected) return@Runnable
            bound = false
            val waited = sinceMillis(bindRequestedAtMillis)
            val reason = bindReason
            val next = scheduleRebind()
            log(
                DiagnosticLogLevel.WARNING,
                "Install watcher did not connect in time. waitedMs=$waited, reason=$reason$next",
            )
            logGivenUp(next)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder?) {
            val (connectMillis, reason) = synchronized(this@NewAppWatcherClient) {
                connected = true
                handler.removeCallbacks(connectTimeout)
                val now = SystemClock.elapsedRealtime()
                backoff.onConnected(now)
                connectedAtMillis = now
                bindRequestedAtMillis?.let { now - it } to bindReason
            }
            val watcher = runCatching { INewAppWatcher.Stub.asInterface(binder ?: error("empty binder")) }
            val result = watcher.mapCatching { it.watch(UserUids.userIdOf(Process.myUid())) }
            result.onSuccess { pid ->
                // A watcher from before the report existed does not answer, which
                // costs the report and nothing else.
                val reportText = watcher.mapCatching { it.startReport() }.getOrNull()
                WatcherStartReport.connectLines(
                    pid = pid,
                    reportText = reportText,
                    connectMillis = connectMillis,
                    zone = ZoneId.systemDefault(),
                    reason = reason,
                    alreadyLogged = ::alreadyLogged,
                ).forEach { log(DiagnosticLogLevel.INFO, it) }
            }.onFailure { error ->
                synchronized(this@NewAppWatcherClient) {
                    if (logBudget.take(SystemClock.elapsedRealtime(), ::logDropped)) {
                        diagnosticLog.error(LOG_SOURCE, "Install watcher did not start.", error)
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            synchronized(this@NewAppWatcherClient) {
                when (WatcherDisconnect.of(bound, connected, shizukuAlive())) {
                    WatcherDisconnect.ALREADY_ENDED -> return
                    WatcherDisconnect.SHIZUKU_GONE -> {
                        // The death listener still reads connectedAtMillis for its line.
                        bound = false
                        connected = false
                        return
                    }
                    WatcherDisconnect.UNEXPECTED -> Unit
                }
                bound = false
                connected = false
                val connectedFor = sinceMillis(connectedAtMillis)
                connectedAtMillis = null
                val next = scheduleRebind()
                log(DiagnosticLogLevel.WARNING, "Install watcher disconnected. connectedForMs=$connectedFor$next")
                logGivenUp(next)
            }
        }
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener {
        // A new Shizuku has no watcher yet, whatever happened under the last one.
        synchronized(this) { backoff.reset() }
        bindIfWanted("shizuku_binder_received")
    }
    private val binderDead = Shizuku.OnBinderDeadListener {
        synchronized(this) {
            if (wanted) {
                log(
                    DiagnosticLogLevel.WARNING,
                    "Install watcher lost Shizuku. Shizuku binder died. bound=$bound, connected=$connected, " +
                        "connectedForMs=${sinceMillis(connectedAtMillis)}",
                )
            }
            bound = false
            connected = false
            connectedAtMillis = null
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
        if (value) bindIfWanted("turned_on") else stop()
    }

    /**
     * Binds a watcher that is wanted but could not be bound when it was asked
     * for, because Shizuku had not granted its permission yet. Nothing else
     * reports the grant, so the caller tries again once the backend is up.
     */
    fun bindIfMissing() = bindIfWanted("backend_connected")

    @Synchronized
    private fun bindIfWanted(reason: String) {
        if (!wanted || bound || !shizukuReady()) return
        handler.removeCallbacks(rebind)
        pendingRebind = null
        runCatching { Shizuku.bindUserService(args, connection) }
            .onSuccess {
                bound = true
                bindRequestedAtMillis = SystemClock.elapsedRealtime()
                // Written with the running line, or with the timeout if none follows.
                bindReason = reason
                handler.postDelayed(connectTimeout, CONNECT_TIMEOUT_MILLIS)
            }
            .onFailure { error ->
                if (logBudget.take(SystemClock.elapsedRealtime(), ::logDropped)) {
                    diagnosticLog.error(LOG_SOURCE, "Unable to bind the install watcher. reason=$reason", error)
                }
            }
    }

    /** Ends the daemon too, which an unbind alone would leave running. */
    @Synchronized
    private fun stop() {
        cancelPending()
        backoff.reset()
        if (!shizukuReady()) return
        runCatching { Shizuku.unbindUserService(args, connection, true) }
            .onSuccess {
                if (bound) {
                    log(
                        DiagnosticLogLevel.INFO,
                        "Install watcher stopped. reason=turned_off, connected=$connected, " +
                            "connectedForMs=${sinceMillis(connectedAtMillis)}",
                    )
                }
                bound = false
                connected = false
                connectedAtMillis = null
            }
    }

    /**
     * Schedules the next start and says what was decided, as the end of the
     * line about the loss that caused it.
     */
    private fun scheduleRebind(): String {
        if (!wanted) return ""
        val delayMillis = backoff.nextDelayMillis(SystemClock.elapsedRealtime())
            ?: return GIVEN_UP
        pendingRebind = "rebind_attempt_${backoff.attempts}"
        handler.removeCallbacks(rebind)
        handler.postDelayed(rebind, delayMillis)
        return ", rebindAttempt=${backoff.attempts}, rebindDelayMs=$delayMillis"
    }

    private fun logGivenUp(next: String) {
        if (next != GIVEN_UP) return
        log(
            DiagnosticLogLevel.WARNING,
            "Install watcher keeps stopping. It starts again with the app or Shizuku. " +
                "attempts=${backoff.attempts}",
        )
    }

    /**
     * Whether this watcher's lines are in the log already. The note is kept
     * with the log, so a new app process that finds the same watcher running,
     * as after every force stop, writes nothing about it.
     */
    private fun alreadyLogged(report: WatcherStartReport): Boolean {
        val identity = report.identity ?: return false
        return !diagnosticLog.noteIfChanged(WATCHER_NOTE_KEY, identity)
    }

    /** Every line goes through the budget, so a restart loop cannot flood the log. */
    @Synchronized
    private fun log(level: DiagnosticLogLevel, message: String) {
        if (!logBudget.take(SystemClock.elapsedRealtime(), ::logDropped)) return
        when (level) {
            // The watcher's lifecycle is kept with the other important lines.
            DiagnosticLogLevel.INFO -> diagnosticLog.info(LOG_SOURCE, message, DiagnosticLogTier.IMPORTANT)
            DiagnosticLogLevel.WARNING -> diagnosticLog.warning(LOG_SOURCE, message)
            DiagnosticLogLevel.ERROR -> diagnosticLog.error(LOG_SOURCE, message)
        }
    }

    private fun logDropped(count: Int) {
        diagnosticLog.warning(LOG_SOURCE, "Install watcher log lines left out. count=$count")
    }

    private fun sinceMillis(startMillis: Long?): String =
        startMillis?.let { (SystemClock.elapsedRealtime() - it).toString() } ?: "unknown"

    private fun cancelPending() {
        handler.removeCallbacks(rebind)
        handler.removeCallbacks(connectTimeout)
    }

    private fun shizukuAlive(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    private fun shizukuReady(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    private companion object {
        const val LOG_SOURCE = "NewAppWatcher"
        const val GIVEN_UP = ", rebind=given_up"
        const val WATCHER_NOTE_KEY = "newAppWatcher.identity"

        /** Longer than the 30 seconds Shizuku itself waits for a service to start. */
        const val CONNECT_TIMEOUT_MILLIS = 45_000L
    }
}
