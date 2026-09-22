package dev.izumi.appopsnext.monitor

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.IBinder
import dev.izumi.appopsnext.AppOpsNextApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps the app process alive so the watch callbacks stay reachable.
 *
 * The callback binders live in this process, so the registration dies with it.
 * That is the cost of reaching AppOps through a forwarded binder call instead of
 * a privileged service of our own, and the ongoing notification is the honest
 * disclosure that something is watching.
 */
class AppOpsMonitorService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var startJob: Job? = null
    private var checkingRequested = false
    private var latestStartId = 0

    private val controller: AppOpsMonitorController
        get() = (application as AppOpsNextApplication).appOpsMonitorController

    override fun onCreate() {
        super.onCreate()
        servicePresent.value = true
        serviceScope.launch {
            controller.failure.drop(1).filterNotNull().collect {
                if (!controller.isRunning) stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        if (intent?.action == ACTION_CLEAR) {
            // Sent with startService from the notification action, so there is no
            // foreground deadline to satisfy here.
            if (!controller.isRunning) {
                stopSelf(startId)
                return START_NOT_STICKY
            }
            controller.clearAccesses()
            return START_STICKY
        }
        // Showing the notification before the self-check finishes is what makes
        // the status bar respond to the switch immediately; it says what is
        // actually happening rather than claiming the monitor is already live.
        checkingRequested = intent?.getBooleanExtra(EXTRA_CHECKING, false) == true
        // The platform allows five seconds between startForegroundService and this
        // call, so nothing may be awaited before it. Reading the settings is disk
        // I/O and a read that fails would let the deadline expire with the service
        // still in the background, which is an ANR rather than a failed start.
        postForeground(checkingRequested, headsUpHint)
        if (startJob?.isActive == true) return START_STICKY
        startJob = serviceScope.launch {
            val settings = runCatching {
                (application as AppOpsNextApplication).userSettingsRepository.settings.first()
            }.getOrElse { error ->
                (application as AppOpsNextApplication).diagnosticLogRepository.error(
                    source = "Monitor",
                    message = "Unable to read the monitor settings.",
                    error = error,
                )
                stopSelf(latestStartId)
                return@launch
            }
            // The channel a session posts on is fixed once it starts, so the only
            // switch is this one, and only when the hint was stale.
            if (settings.monitorHeadsUp != headsUpHint) {
                headsUpHint = settings.monitorHeadsUp
                postForeground(checkingRequested, headsUpHint)
            }
            // A sticky restart must not resurrect a monitor the user disabled.
            if (!checkingRequested && !settings.backgroundMonitor) {
                stopSelf(latestStartId)
                return@launch
            }
            if (checkingRequested) return@launch
            if (controller.start().isFailure) stopSelf(latestStartId)
            else controller.refreshNotifications()
        }
        return START_STICKY
    }

    private fun postForeground(checking: Boolean, headsUp: Boolean) {
        // Before a session has started there is no point count to show, and
        // starting one checks the registration, so it is shown as a check.
        val points = if (checking) null else controller.watchedPointCount
        startForeground(
            MonitorNotifier.ONGOING_NOTIFICATION_ID,
            MonitorNotifier(this).ongoingNotification(
                checking = points == null,
                useAlertChannel = headsUp,
                watchedPoints = points ?: 0,
            ),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
    }

    /**
     * The notification text is built once when the service starts, so a change
     * of app language would otherwise leave it in the previous language until
     * the monitor was restarted.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // The controller rebuilds the ongoing notification too, so posting a
        // plain one here first would briefly drop the accesses folded into it.
        controller.refreshNotifications()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        controller.stop(clearFailure = false)
        servicePresent.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private val servicePresent = MutableStateFlow(false)
        const val ACTION_CLEAR = "dev.izumi.appopsnext.monitor.CLEAR"
        private const val EXTRA_CHECKING = "checking"
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        /**
         * The last heads-up setting this process saw, so the foreground
         * notification can be posted before the real value can be read.
         */
        @Volatile
        private var headsUpHint = false

        fun start(context: Context, checking: Boolean = false) {
            context.startForegroundService(
                Intent(context, AppOpsMonitorService::class.java)
                    .putExtra(EXTRA_CHECKING, checking),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AppOpsMonitorService::class.java))
        }

        /**
         * Waits for the service to actually go away, bounded: a start command
         * arriving at the same moment can keep it up, and the caller must not be
         * left waiting for a teardown that is never coming.
         */
        suspend fun stopAndAwait(context: Context) {
            stop(context)
            withTimeoutOrNull(STOP_TIMEOUT_MILLIS) { servicePresent.first { !it } }
        }
    }
}
