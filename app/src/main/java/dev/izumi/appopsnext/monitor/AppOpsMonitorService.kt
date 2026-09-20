package dev.izumi.appopsnext.monitor

import android.app.Service
import android.content.Context
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.IBinder
import dev.izumi.appopsnext.AppOpsNextApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the app process alive so the watch callbacks stay reachable.
 *
 * The callback binders live in this process, so the registration dies with it.
 * That is the cost of reaching AppOps through a forwarded binder call instead of
 * a privileged service of our own, and the ongoing notification is the honest
 * disclosure that something is watching.
 */
class AppOpsMonitorService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val controller: AppOpsMonitorController
        get() = (application as AppOpsNextApplication).appOpsMonitorController

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(
            MonitorNotifier.ONGOING_NOTIFICATION_ID,
            MonitorNotifier(this).ongoingNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        if (!controller.isRunning) {
            serviceScope.launch {
                // Staying up after a failed registration would leave an ongoing
                // notification claiming to watch something while nothing is
                // registered. The setting is left on so the next launch retries,
                // because the usual cause is Shizuku not being ready yet.
                if (controller.start().isFailure) stopSelf()
            }
        }
        return START_STICKY
    }

    /**
     * The notification text is built once when the service starts, so a change
     * of app language would otherwise leave it in the previous language until
     * the monitor was restarted.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        getSystemService(NotificationManager::class.java).notify(
            MonitorNotifier.ONGOING_NOTIFICATION_ID,
            MonitorNotifier(this).ongoingNotification(),
        )
        controller.refreshNotifications()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        controller.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val ACTION_STOP = "dev.izumi.appopsnext.monitor.STOP"

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, AppOpsMonitorService::class.java),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, AppOpsMonitorService::class.java)
                    .setAction(ACTION_STOP),
            )
        }
    }
}
