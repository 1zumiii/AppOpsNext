package dev.izumi.appopsnext.appops

import android.Manifest
import dev.izumi.appopsnext.appops.model.AppOpNames

object AppOpRuntimePermissionCatalog {
    fun requiredPermission(operationName: String): String? =
        permissionsByOperation[AppOpNames.shellName(operationName)]

    private val permissionsByOperation = mapOf(
        "CAMERA" to Manifest.permission.CAMERA,
        "RECORD_AUDIO" to Manifest.permission.RECORD_AUDIO,
        "FINE_LOCATION" to Manifest.permission.ACCESS_FINE_LOCATION,
        "COARSE_LOCATION" to Manifest.permission.ACCESS_COARSE_LOCATION,
        "POST_NOTIFICATION" to Manifest.permission.POST_NOTIFICATIONS,
        "READ_CONTACTS" to Manifest.permission.READ_CONTACTS,
        "WRITE_CONTACTS" to Manifest.permission.WRITE_CONTACTS,
        "READ_CALENDAR" to Manifest.permission.READ_CALENDAR,
        "WRITE_CALENDAR" to Manifest.permission.WRITE_CALENDAR,
        "READ_CALL_LOG" to Manifest.permission.READ_CALL_LOG,
        "WRITE_CALL_LOG" to Manifest.permission.WRITE_CALL_LOG,
        "CALL_PHONE" to Manifest.permission.CALL_PHONE,
        "READ_PHONE_STATE" to Manifest.permission.READ_PHONE_STATE,
        "READ_PHONE_NUMBERS" to Manifest.permission.READ_PHONE_NUMBERS,
        "ANSWER_PHONE_CALLS" to Manifest.permission.ANSWER_PHONE_CALLS,
        "READ_SMS" to Manifest.permission.READ_SMS,
        "SEND_SMS" to Manifest.permission.SEND_SMS,
        "RECEIVE_SMS" to Manifest.permission.RECEIVE_SMS,
        "RECEIVE_MMS" to Manifest.permission.RECEIVE_MMS,
        "BODY_SENSORS" to Manifest.permission.BODY_SENSORS,
        "ACTIVITY_RECOGNITION" to Manifest.permission.ACTIVITY_RECOGNITION,
        "READ_MEDIA_AUDIO" to Manifest.permission.READ_MEDIA_AUDIO,
        "READ_MEDIA_VIDEO" to Manifest.permission.READ_MEDIA_VIDEO,
        "READ_MEDIA_IMAGES" to Manifest.permission.READ_MEDIA_IMAGES,
        "BLUETOOTH_SCAN" to Manifest.permission.BLUETOOTH_SCAN,
        "BLUETOOTH_CONNECT" to Manifest.permission.BLUETOOTH_CONNECT,
        "BLUETOOTH_ADVERTISE" to Manifest.permission.BLUETOOTH_ADVERTISE,
        "NEARBY_WIFI_DEVICES" to Manifest.permission.NEARBY_WIFI_DEVICES,
    )
}
