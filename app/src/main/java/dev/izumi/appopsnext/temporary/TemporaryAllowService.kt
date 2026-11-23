package dev.izumi.appopsnext.temporary

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import dev.izumi.appopsnext.AppOpsNextApplication
import dev.izumi.appopsnext.MainActivity
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.guard.GuardWriteTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal class TemporaryAllowService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var monitor: Job? = null
    private var status: Job? = null
    private var latestStartId = 0
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }
    private val app get() = application as AppOpsNextApplication
    private var currentNotification: Notification? = null

    override fun onCreate() {
        super.onCreate()
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.temporary_allow_option), NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        startForeground(
            NOTIFICATION_ID, currentNotification ?: notification().build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        if (status?.isActive != true) {
            status = scope.launch {
                app.temporaryAllowController.active.map { entries ->
                    entries.map { it.target }.sortedWith(compareBy({ it.packageName }, { it.stableOperationName }))
                }.distinctUntilChanged().collect { targets ->
                    if (targets.isNotEmpty()) updateNotifications(targets)
                    else clearItems()
                }
            }
        }
        if (monitor?.isActive != true) {
            monitor = scope.launch {
                val controller = app.temporaryAllowController
                while (controller.check()) delay(CHECK_INTERVAL_MILLIS)
                stopSelfResult(latestStartId)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        clearItems()
        super.onDestroy()
    }

    private fun notification(): Notification.Builder {
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_monitor)
            .setContentTitle(getString(R.string.temporary_allow_option))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
    }

    private data class NotificationItem(val tag: String, val label: String, val packageName: String)

    private fun updateNotifications(targets: List<GuardWriteTarget>) {
        val items = targets.map { target ->
            val tag = "${target.packageName}|${target.stableOperationName}"
            val label = getString(
                R.string.temporary_allow_notification_item,
                app.guardNotifier.appLabel(target.packageName),
                app.guardNotifier.operationLabel(target.stableOperationName),
            )
            NotificationItem(tag, label, target.packageName)
        }
        val first = items.first()
        val showGroup = items.size > 1 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val summary = notification().setContentIntent(openDetails(first.packageName))
        if (showGroup) {
            summary.setGroup(CHANNEL).setGroupSummary(true)
                .setStyle(Notification.InboxStyle().also { style -> items.forEach { style.addLine(it.label) } })
        } else {
            summary.setContentTitle(first.label)
        }
        val updated = summary.build()
        startForeground(NOTIFICATION_ID, updated, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        currentNotification = updated

        clearItemsExcept(if (showGroup) items.mapTo(mutableSetOf()) { it.tag } else emptySet())
        if (showGroup) {
            for ((tag, label, packageName) in items) {
                notifications.notify(
                    tag, NOTIFICATION_ID,
                    notification().setContentTitle(label).setGroup(CHANNEL)
                        .setContentIntent(openDetails(packageName)).build(),
                )
            }
        }
    }

    private fun clearItems() {
        clearItemsExcept(emptySet())
        currentNotification = null
    }

    private fun clearItemsExcept(keep: Set<String>) {
        notifications.activeNotifications.asSequence()
            .filter { it.id == NOTIFICATION_ID }
            .mapNotNull { it.tag }
            .filterNot { it in keep }
            .forEach { notifications.cancel(it, NOTIFICATION_ID) }
    }

    private fun openDetails(packageName: String): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).apply {
            data = Uri.Builder().scheme("appopsnext").authority("temporary-allow").appendPath(packageName).build()
            putExtra(EXTRA_OPEN_APP, packageName)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val EXTRA_OPEN_APP = "temporary_allow_app"
        private const val CHANNEL = "temporary_allow"
        private const val NOTIFICATION_ID = 4022
        private const val CHECK_INTERVAL_MILLIS = 3_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, TemporaryAllowService::class.java))
        }
    }
}
