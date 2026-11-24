package dev.izumi.appopsnext

import android.app.Notification
import android.app.Service
import android.content.pm.ServiceInfo
import android.os.Build

/**
 * Starts a service in the foreground as the special-use type the manifest declares.
 *
 * The type exists from Android 14. On Android 13 the call goes without a type, so
 * the system takes it from the manifest, which on that version does not know the
 * value and requires no permission for it.
 */
internal fun Service.startSpecialUseForeground(id: Int, notification: Notification) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    } else {
        startForeground(id, notification)
    }
}
