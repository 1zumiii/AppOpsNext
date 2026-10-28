package dev.izumi.appopsnext.guard

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
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpNames
import dev.izumi.appopsnext.presentation.app_detail.AppOpDisplayCatalog

class GuardNotifier(private val context: Context) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    /** One notification per check, replaced by the next one, so a busy day does not stack up. */
    fun notifyEvents(events: List<GuardEvent>) {
        if (events.isEmpty() || !canPost()) return
        createChannels()
        val lines = events.map(::describe)
        val title = context.resources.getQuantityString(R.plurals.guard_notification_title, events.size, events.size)
        val notification = Notification.Builder(context, CHANNEL_EVENTS)
            .setSmallIcon(R.drawable.ic_notification_monitor)
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(Notification.InboxStyle().also { style -> lines.take(MAX_LINES).forEach(style::addLine) })
            .setContentIntent(openGuardScreen())
            .setCategory(Notification.CATEGORY_STATUS)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_EVENTS, notification)
    }

    /** The ongoing notification the realtime service must show. */
    fun watchingNotification(ruleCount: Int): Notification {
        createChannels()
        return Notification.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification_monitor)
            .setContentTitle(context.getString(R.string.guard_service_title))
            .setContentText(
                context.resources.getQuantityString(R.plurals.guard_service_text, ruleCount, ruleCount),
            )
            .setContentIntent(openGuardScreen())
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    fun describe(event: GuardEvent): String {
        val app = appLabel(event.packageName)
        val operation = operationLabel(event.stableOperationName)
        val resource = when (event.kind) {
            GuardEventKind.DRIFT -> R.string.guard_event_drift
            GuardEventKind.RESTORED -> R.string.guard_event_restored
            GuardEventKind.RESTORE_FAILED -> R.string.guard_event_restore_failed
            GuardEventKind.RESTORE_LIMIT -> R.string.guard_event_restore_limit
        }
        return context.getString(
            resource,
            app,
            operation,
            context.getString(modeLabelRes(event.observedMode)),
            context.getString(modeLabelRes(event.expectedMode)),
        )
    }

    private fun appLabel(packageName: String): String =
        runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)

    private fun operationLabel(stableOperationName: String): String {
        val shellName = AppOpNames.shellName(stableOperationName)
        return AppOpDisplayCatalog.labelResOf(shellName)?.let(context::getString) ?: shellName
    }

    private fun canPost(): Boolean =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openGuardScreen(): PendingIntent = PendingIntent.getActivity(
        context,
        NOTIFICATION_EVENTS,
        Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_GUARD, true)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createChannels() {
        notificationManager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_EVENTS,
                    context.getString(R.string.guard_channel_events),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                NotificationChannel(
                    CHANNEL_STATUS,
                    context.getString(R.string.guard_channel_status),
                    NotificationManager.IMPORTANCE_MIN,
                ),
            ),
        )
    }

    companion object {
        fun modeLabelRes(mode: AppOpMode): Int = when (mode) {
            AppOpMode.ALLOW -> R.string.app_op_mode_allow
            AppOpMode.IGNORE -> R.string.app_op_mode_ignore
            AppOpMode.DENY -> R.string.app_op_mode_deny
            AppOpMode.DEFAULT -> R.string.app_op_mode_default
            AppOpMode.FOREGROUND -> R.string.app_op_mode_foreground
        }

        const val EXTRA_OPEN_GUARD = "open_guard"
        const val NOTIFICATION_STATUS = 4021
        private const val NOTIFICATION_EVENTS = 4020
        private const val CHANNEL_EVENTS = "guard_events"
        private const val CHANNEL_STATUS = "guard_status"
        private const val MAX_LINES = 6
    }
}
