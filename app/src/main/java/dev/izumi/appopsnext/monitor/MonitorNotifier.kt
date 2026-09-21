package dev.izumi.appopsnext.monitor

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
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

    /**
     * The single notification the monitor keeps while it runs.
     *
     * Reported accesses are folded into this one rather than posted separately,
     * because two notifications from the same app compete for the one status-bar
     * icon and the ongoing one wins, which hid the access count behind an icon
     * that never changed. The running state stays visible as the sub-text so
     * merging the two does not lose it.
     */
    fun ongoingNotification(
        accesses: List<MonitoredAccess> = emptyList(),
        checking: Boolean = false,
        useAlertChannel: Boolean = false,
        alert: Boolean = false,
    ): Notification {
        createChannels()
        // A channel's importance is fixed when it is created, so the way to let
        // this one notification interrupt is to post it on the loud channel
        // instead of adding a second notification beside it.
        val builder = Notification
            .Builder(context, if (useAlertChannel) CHANNEL_ALERT else CHANNEL_STATUS)
            .setContentIntent(openAppIntent(OPEN_REQUEST_CODE))
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(!alert)
            .setGroup("monitor_events")
            .setGroupAlertBehavior(if (alert) Notification.GROUP_ALERT_ALL else Notification.GROUP_ALERT_SUMMARY)

        return when {
            checking -> builder
                .setSmallIcon(R.drawable.ic_notification_monitor)
                .setContentTitle(context.getString(R.string.monitor_ongoing_title))
                .setContentText(context.getString(R.string.monitor_checking))
                .build()

            accesses.isEmpty() -> builder
                .setSmallIcon(R.drawable.ic_notification_monitor)
                .setContentTitle(context.getString(R.string.monitor_ongoing_title))
                .setContentText(context.getString(R.string.monitor_ongoing_text))
                .build()

            else -> {
                val latest = accesses.first()
                // Dismissing is the natural way to say "I have read these", and
                // the action covers the case where the platform pins the
                // notification and it cannot be dismissed at all.
                // Repeats are folded into one entry, so the number of entries is
                // not the number of accesses the user was told about.
                val total = accesses.sumOf(MonitoredAccess::count)
                builder
                    .setSmallIcon(MonitorStatusIcon.create(context, total))
                    .setSubText(context.getString(R.string.monitor_ongoing_title))
                    .setContentTitle(latest.appLabel)
                    .setContentText(describe(latest))
                    .setStyle(inboxStyle(accesses))
                    .setNumber(total)
                    .setWhen(latest.observedAtMillis)
                    .setShowWhen(true)
                    .setDeleteIntent(clearIntent())
                    .addAction(
                        Notification.Action.Builder(
                            Icon.createWithResource(context, R.drawable.ic_action_close),
                            context.getString(R.string.monitor_clear),
                            clearIntent(),
                        ).build(),
                    )
                    .build()
            }
        }
    }

    /**
     * Updates the ongoing notification with the accesses so far.
     *
     * The channel is fixed for the session. Only a new entry requests an alert;
     * repeats and refreshes update silently on that same channel.
     *
     * @param accesses most recent first.
     */
    fun notifyAccesses(accesses: List<MonitoredAccess>, useAlertChannel: Boolean, alert: Boolean) {
        if (accesses.isEmpty()) return
        if (
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationManager.notify(
            ONGOING_NOTIFICATION_ID,
            ongoingNotification(accesses, useAlertChannel = useAlertChannel, alert = alert),
        )
    }

    /** Re-posts the ongoing notification in its no-accesses form. */
    fun postOngoing(useAlertChannel: Boolean) {
        notificationManager.notify(ONGOING_NOTIFICATION_ID,
            ongoingNotification(useAlertChannel = useAlertChannel))
    }

    fun cancel() { notificationManager.cancel(ONGOING_NOTIFICATION_ID) }

    private fun inboxStyle(accesses: List<MonitoredAccess>): Notification.InboxStyle =
        Notification.InboxStyle().apply {
            accesses.take(MAX_LINES).map(::describe).forEach(::addLine)
            if (accesses.size > MAX_LINES) {
                setSummaryText(
                    context.getString(
                        R.string.monitor_more_accesses,
                        accesses.size - MAX_LINES,
                    ),
                )
            }
        }

    /**
     * Caps the application label so the rest of the line survives.
     *
     * An inbox line is a single line: a long label pushes the operation and the
     * count off the end into an ellipsis, which loses exactly the part the line
     * exists to show.
     */
    private fun shortLabel(label: String): String =
        if (label.length <= MAX_LABEL_CHARS) {
            label
        } else {
            label.take(MAX_LABEL_CHARS - 1).trimEnd() + "\u2026"
        }

    /** `14:32 · WeChat used the clipboard · x3` */
    private fun describe(access: MonitoredAccess): String {
        val operationLabel = AppOpDisplayCatalog.labelResOf(access.operationName)
            ?.let(context::getString)
            ?: access.operationName
        val time = timeFormatter.format(
            Instant.ofEpochMilli(access.observedAtMillis),
        )
        val line = context.getString(
            if (access.allowed) {
                R.string.monitor_access_allowed
            } else {
                R.string.monitor_access_refused
            },
            time,
            shortLabel(access.appLabel),
            operationLabel,
        )
        return if (access.count > 1) {
            context.getString(R.string.monitor_access_count, line, access.count)
        } else {
            line
        }
    }

    private val timeFormatter: DateTimeFormatter
        get() = DateTimeFormatter
            .ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault())

    private fun clearIntent(): PendingIntent =
        PendingIntent.getService(
            context,
            CLEAR_REQUEST_CODE,
            Intent(context, AppOpsMonitorService::class.java)
                .setAction(AppOpsMonitorService.ACTION_CLEAR),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

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
                CHANNEL_ALERT,
                context.getString(R.string.monitor_channel_alert),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        // Earlier releases posted accesses to their own quiet channel and, before
        // that, to a single default-importance one. Both are now folded into the
        // ongoing notification.
        notificationManager.deleteNotificationChannel(LEGACY_CHANNEL_ACCESS)
        notificationManager.deleteNotificationChannel(LEGACY_CHANNEL_SILENT)
    }

    companion object {
        const val ONGOING_NOTIFICATION_ID = 4011
        private const val OPEN_REQUEST_CODE = 4012
        private const val CLEAR_REQUEST_CODE = 4014
        private const val MAX_LINES = 6
        private const val MAX_LABEL_CHARS = 16
        private const val CHANNEL_STATUS = "monitor_status"
        private const val CHANNEL_ALERT = "monitor_access_alert"
        private const val LEGACY_CHANNEL_ACCESS = "monitor_access"
        private const val LEGACY_CHANNEL_SILENT = "monitor_access_silent"
    }
}
