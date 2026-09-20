package dev.izumi.appopsnext.monitor

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dev.izumi.appopsnext.MainActivity
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.presentation.app_detail.AppOpDisplayCatalog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class MonitorNotifier(
    private val context: Context,
) {
    private val notificationManager =
        context.getSystemService(NotificationManager::class.java)

    fun ongoingNotification(): Notification {
        createChannels()
        return Notification.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification_monitor)
            .setContentTitle(context.getString(R.string.monitor_ongoing_title))
            .setContentText(context.getString(R.string.monitor_ongoing_text))
            .setContentIntent(openAppIntent(OPEN_REQUEST_CODE))
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }

    /**
     * Posts the accesses as one notification that is updated in place.
     *
     * [headsUp] selects between two channels because a channel's importance is
     * fixed when it is created and cannot be raised later from code, so the
     * quiet and the interrupting variant have to be separate channels.
     *
     * @param accesses most recent first.
     */
    fun notifyAccesses(accesses: List<MonitoredAccess>, headsUp: Boolean) {
        if (accesses.isEmpty()) return
        if (
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        createChannels()
        val latest = accesses.first()
        val lines = accesses.take(MAX_LINES).map(::describe)
        val style = Notification.InboxStyle().apply {
            lines.forEach(::addLine)
            if (accesses.size > MAX_LINES) {
                setSummaryText(
                    context.getString(
                        R.string.monitor_more_accesses,
                        accesses.size - MAX_LINES,
                    ),
                )
            }
        }
        val notification = Notification
            .Builder(context, if (headsUp) CHANNEL_ALERT else CHANNEL_SILENT)
            .setSmallIcon(MonitorStatusIcon.create(context, accesses.size))
            .setContentTitle(latest.appLabel)
            .setContentText(describe(latest))
            .setStyle(style)
            .setNumber(accesses.size)
            .setWhen(latest.observedAtMillis)
            .setShowWhen(true)
            .setContentIntent(openAppIntent(OPEN_REQUEST_CODE + 1))
            .setCategory(Notification.CATEGORY_STATUS)
            .setOnlyAlertOnce(!headsUp)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(ACCESS_NOTIFICATION_ID, notification)
    }

    fun clearAccesses() {
        notificationManager.cancel(ACCESS_NOTIFICATION_ID)
    }

    /** `14:32 · WeChat used the clipboard` */
    private fun describe(access: MonitoredAccess): String {
        val operationLabel = AppOpDisplayCatalog.labelResOf(access.operationName)
            ?.let(context::getString)
            ?: access.operationName
        val time = timeFormatter.format(
            Instant.ofEpochMilli(access.observedAtMillis),
        )
        return context.getString(
            if (access.allowed) {
                R.string.monitor_access_allowed
            } else {
                R.string.monitor_access_refused
            },
            time,
            access.appLabel,
            operationLabel,
        )
    }

    private val timeFormatter: DateTimeFormatter
        get() = DateTimeFormatter
            .ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault())

    private fun openAppIntent(requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createChannels() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS,
                context.getString(R.string.monitor_channel_status),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SILENT,
                context.getString(R.string.monitor_channel_silent),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT,
                context.getString(R.string.monitor_channel_alert),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        // The first release posted accesses to a single default-importance
        // channel, which could neither interrupt nor stay quiet on request.
        notificationManager.deleteNotificationChannel(LEGACY_CHANNEL_ACCESS)
    }

    companion object {
        const val ONGOING_NOTIFICATION_ID = 4011
        private const val ACCESS_NOTIFICATION_ID = 4013
        private const val OPEN_REQUEST_CODE = 4012
        private const val MAX_LINES = 6
        private const val CHANNEL_STATUS = "monitor_status"
        private const val CHANNEL_SILENT = "monitor_access_silent"
        private const val CHANNEL_ALERT = "monitor_access_alert"
        private const val LEGACY_CHANNEL_ACCESS = "monitor_access"
    }
}
