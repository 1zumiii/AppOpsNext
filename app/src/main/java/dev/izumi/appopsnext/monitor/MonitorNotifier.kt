package dev.izumi.appopsnext.monitor

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
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
        /** Package and operation pairs being watched, shown while nothing is reported. */
        watchedPoints: Int = 0,
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
                .setContentText(context.getString(R.string.monitor_ongoing_text, watchedPoints))
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
                    // A collapsed notification has one line of text, so the count
                    // rides on the title and the header already shows the time.
                    .setContentTitle(withCount(shortLabel(latest.appLabel), latest.count))
                    .setContentText(action(latest))
                    .setStyle(bigTextStyle(accesses, total))
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
    fun postOngoing(useAlertChannel: Boolean, watchedPoints: Int) {
        notificationManager.notify(ONGOING_NOTIFICATION_ID,
            ongoingNotification(useAlertChannel = useAlertChannel, watchedPoints = watchedPoints))
    }

    fun cancel() { notificationManager.cancel(ONGOING_NOTIFICATION_ID) }

    /**
     * Two lines per access: when, who and how often in bold, then what happened.
     *
     * An inbox line cannot wrap, so a long entry lost its trailing count to an
     * ellipsis. Big text wraps, at the cost of fewer entries in the same height.
     *
     * The first entry would repeat the collapsed title, so the expanded title is
     * the total instead, with the refusals in it named because the status-bar
     * count includes them too.
     */
    private fun bigTextStyle(accesses: List<MonitoredAccess>, total: Int): Notification.BigTextStyle =
        Notification.BigTextStyle().apply {
            val denied = accesses.filterNot(MonitoredAccess::allowed).sumOf(MonitoredAccess::count)
            setBigContentTitle(
                if (denied > 0) {
                    context.getString(R.string.monitor_reported_total_denied, total, denied)
                } else {
                    context.getString(R.string.monitor_reported_total, total)
                },
            )
            val text = SpannableStringBuilder()
            accesses.take(MAX_ENTRIES).forEachIndexed { index, access ->
                if (index > 0) text.append('\n')
                val heading = context.getString(
                    R.string.monitor_access_heading,
                    timeFormatter.format(Instant.ofEpochMilli(access.observedAtMillis)),
                    shortLabel(access.appLabel),
                )
                text.append(
                    withCount(heading, access.count),
                    StyleSpan(Typeface.BOLD),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                text.append('\n').append(action(access))
            }
            bigText(text)
            if (accesses.size > MAX_ENTRIES) {
                setSummaryText(
                    context.getString(
                        R.string.monitor_more_accesses,
                        accesses.size - MAX_ENTRIES,
                    ),
                )
            }
        }

    /**
     * Caps the application label so the count after it survives.
     *
     * The collapsed title is a single line, and in the expanded list a long label
     * would wrap an entry onto a third line.
     */
    private fun shortLabel(label: String): String =
        if (label.length <= MAX_LABEL_CHARS) {
            label
        } else {
            label.take(MAX_LABEL_CHARS - 1).trimEnd() + "\u2026"
        }

    /** `使用了读取剪贴板`, the line that says what happened. */
    private fun action(access: MonitoredAccess): String {
        val operationLabel = AppOpDisplayCatalog.labelResOf(access.operationName)
            ?.let(context::getString)
            ?: access.operationName
        return context.getString(
            if (access.allowed) {
                R.string.monitor_access_allowed
            } else {
                R.string.monitor_access_refused
            },
            operationLabel,
        )
    }

    /** `微信 · x3`; a single access carries no count. */
    private fun withCount(text: String, count: Int): String =
        if (count > 1) context.getString(R.string.monitor_access_count, text, count) else text

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
        private const val MAX_ENTRIES = 4
        private const val MAX_LABEL_CHARS = 16
        private const val CHANNEL_STATUS = "monitor_status"
        private const val CHANNEL_ALERT = "monitor_access_alert"
        private const val LEGACY_CHANNEL_ACCESS = "monitor_access"
        private const val LEGACY_CHANNEL_SILENT = "monitor_access_silent"
    }
}
