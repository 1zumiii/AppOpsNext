package dev.izumi.appopsnext.newapps

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

class NewAppPolicyNotifier(
    private val context: Context,
) {
    private val notificationManager =
        context.getSystemService(NotificationManager::class.java)

    fun notifyCompleted(
        packageName: String,
        appLabel: String,
        successCount: Int,
        failureCount: Int,
    ) {
        if (
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        createChannel()
        val openAppIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_new_app)
            .setContentTitle(
                context.getString(
                    R.string.new_app_policy_notification_title,
                    appLabel,
                ),
            )
            .setContentText(
                context.getString(
                    R.string.new_app_policy_notification_result,
                    successCount,
                    failureCount,
                ),
            )
            .setContentIntent(openAppIntent)
            .setCategory(Notification.CATEGORY_STATUS)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(packageName.hashCode(), notification)
    }

    private fun createChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(
                    R.string.new_app_policy_notification_channel,
                ),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    private companion object {
        const val CHANNEL_ID = "new_app_policy_results"
        const val NOTIFICATION_REQUEST_CODE = 2_201
    }
}
