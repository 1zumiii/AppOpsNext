package dev.izumi.appopsnext.presentation.app_detail

import androidx.annotation.StringRes
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.appops.model.AppOpEntry
import dev.izumi.appopsnext.appops.model.AppOpNames
import dev.izumi.appopsnext.appops.model.AppOpScope

data class AppOpDisplayItem(
    val operationName: String,
    @StringRes val labelRes: Int?,
    val mode: String,
    val details: String?,
    val scope: AppOpScope,
    val priority: Int,
)

data class KnownAppOp(
    val stableName: String,
    val shellName: String,
    @StringRes val labelRes: Int,
    val priority: Int,
)

object AppOpDisplayCatalog {
    fun build(
        entries: List<AppOpEntry>,
        query: String,
        labelResolver: (Int) -> String,
        alternateLabelResolver: (Int) -> String = labelResolver,
    ): List<AppOpDisplayItem> {
        val normalizedQuery = query.trim()
        return entries
            .groupBy { normalize(it.name) }
            .map { (_, scopedEntries) ->
                val effectiveEntry = scopedEntries
                    .firstOrNull { it.scope == AppOpScope.UID }
                    ?: scopedEntries.first()
                val metadata = metadataByOperation[normalize(effectiveEntry.name)]
                AppOpDisplayItem(
                    operationName = effectiveEntry.name,
                    labelRes = metadata?.labelRes,
                    mode = effectiveEntry.mode,
                    details = effectiveEntry.details
                        ?: scopedEntries.firstNotNullOfOrNull(AppOpEntry::details),
                    scope = effectiveEntry.scope,
                    priority = metadata?.priority ?: PRIORITY_OTHER,
                )
            }
            .filter { item ->
                normalizedQuery.isEmpty() ||
                    item.operationName.contains(
                        normalizedQuery,
                        ignoreCase = true,
                    ) ||
                    item.labelRes?.let(labelResolver)?.contains(
                        normalizedQuery,
                        ignoreCase = true,
                    ) == true ||
                    item.labelRes?.let(alternateLabelResolver)?.contains(
                        normalizedQuery,
                        ignoreCase = true,
                    ) == true
            }
            .sortedWith(
                compareBy<AppOpDisplayItem> { it.priority }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { item ->
                        item.labelRes?.let(labelResolver) ?: item.operationName
                    }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) {
                        it.operationName
                    },
            )
    }

    fun knownOperations(): List<KnownAppOp> =
        metadataByOperation
            .map { (shellName, metadata) ->
                KnownAppOp(
                    stableName = AppOpNames.stableName(shellName),
                    shellName = shellName,
                    labelRes = metadata.labelRes,
                    priority = metadata.priority,
                )
            }
            .sortedWith(
                compareBy<KnownAppOp> { it.priority }
                    .thenBy(KnownAppOp::shellName),
            )

    private fun normalize(operationName: String): String =
        AppOpNames.shellName(operationName)

    private data class Metadata(
        @StringRes val labelRes: Int,
        val priority: Int,
    )

    private const val PRIORITY_PRIVACY = 0
    private const val PRIORITY_COMMUNICATION = 10
    private const val PRIORITY_FILES_AND_DEVICES = 20
    private const val PRIORITY_SYSTEM_BEHAVIOR = 30
    private const val PRIORITY_OTHER = 100

    private val metadataByOperation = mapOf(
        "CAMERA" to Metadata(R.string.app_op_label_camera, PRIORITY_PRIVACY),
        "RECORD_AUDIO" to Metadata(
            R.string.app_op_label_record_audio,
            PRIORITY_PRIVACY,
        ),
        "COARSE_LOCATION" to Metadata(
            R.string.app_op_label_coarse_location,
            PRIORITY_PRIVACY,
        ),
        "FINE_LOCATION" to Metadata(
            R.string.app_op_label_fine_location,
            PRIORITY_PRIVACY,
        ),
        "READ_CLIPBOARD" to Metadata(
            R.string.app_op_label_read_clipboard,
            PRIORITY_PRIVACY,
        ),
        "WRITE_CLIPBOARD" to Metadata(
            R.string.app_op_label_write_clipboard,
            PRIORITY_PRIVACY,
        ),
        "POST_NOTIFICATION" to Metadata(
            R.string.app_op_label_post_notification,
            PRIORITY_PRIVACY,
        ),
        "BODY_SENSORS" to Metadata(
            R.string.app_op_label_body_sensors,
            PRIORITY_PRIVACY,
        ),
        "ACTIVITY_RECOGNITION" to Metadata(
            R.string.app_op_label_activity_recognition,
            PRIORITY_PRIVACY,
        ),
        "READ_CONTACTS" to Metadata(
            R.string.app_op_label_read_contacts,
            PRIORITY_COMMUNICATION,
        ),
        "WRITE_CONTACTS" to Metadata(
            R.string.app_op_label_write_contacts,
            PRIORITY_COMMUNICATION,
        ),
        "GET_ACCOUNTS" to Metadata(
            R.string.app_op_label_get_accounts,
            PRIORITY_COMMUNICATION,
        ),
        "READ_CALL_LOG" to Metadata(
            R.string.app_op_label_read_call_log,
            PRIORITY_COMMUNICATION,
        ),
        "WRITE_CALL_LOG" to Metadata(
            R.string.app_op_label_write_call_log,
            PRIORITY_COMMUNICATION,
        ),
        "CALL_PHONE" to Metadata(
            R.string.app_op_label_call_phone,
            PRIORITY_COMMUNICATION,
        ),
        "READ_PHONE_STATE" to Metadata(
            R.string.app_op_label_read_phone_state,
            PRIORITY_COMMUNICATION,
        ),
        "READ_PHONE_NUMBERS" to Metadata(
            R.string.app_op_label_read_phone_numbers,
            PRIORITY_COMMUNICATION,
        ),
        "ANSWER_PHONE_CALLS" to Metadata(
            R.string.app_op_label_answer_phone_calls,
            PRIORITY_COMMUNICATION,
        ),
        "READ_SMS" to Metadata(
            R.string.app_op_label_read_sms,
            PRIORITY_COMMUNICATION,
        ),
        "SEND_SMS" to Metadata(
            R.string.app_op_label_send_sms,
            PRIORITY_COMMUNICATION,
        ),
        "RECEIVE_SMS" to Metadata(
            R.string.app_op_label_receive_sms,
            PRIORITY_COMMUNICATION,
        ),
        "RECEIVE_MMS" to Metadata(
            R.string.app_op_label_receive_mms,
            PRIORITY_COMMUNICATION,
        ),
        "READ_CALENDAR" to Metadata(
            R.string.app_op_label_read_calendar,
            PRIORITY_COMMUNICATION,
        ),
        "WRITE_CALENDAR" to Metadata(
            R.string.app_op_label_write_calendar,
            PRIORITY_COMMUNICATION,
        ),
        "READ_EXTERNAL_STORAGE" to Metadata(
            R.string.app_op_label_read_external_storage,
            PRIORITY_FILES_AND_DEVICES,
        ),
        "WRITE_EXTERNAL_STORAGE" to Metadata(
            R.string.app_op_label_write_external_storage,
            PRIORITY_FILES_AND_DEVICES,
        ),
        "READ_MEDIA_AUDIO" to Metadata(
            R.string.app_op_label_read_media_audio,
            PRIORITY_FILES_AND_DEVICES,
        ),
        "READ_MEDIA_VIDEO" to Metadata(
            R.string.app_op_label_read_media_video,
            PRIORITY_FILES_AND_DEVICES,
        ),
        "READ_MEDIA_IMAGES" to Metadata(
            R.string.app_op_label_read_media_images,
            PRIORITY_FILES_AND_DEVICES,
        ),
        "READ_MEDIA_VISUAL_USER_SELECTED" to Metadata(
            R.string.app_op_label_selected_media,
            PRIORITY_FILES_AND_DEVICES,
        ),
        "ACCESS_MEDIA_LOCATION" to Metadata(
            R.string.app_op_label_media_location,
            PRIORITY_FILES_AND_DEVICES,
        ),
        "BLUETOOTH_SCAN" to Metadata(
            R.string.app_op_label_bluetooth_scan,
            PRIORITY_FILES_AND_DEVICES,
        ),
        "BLUETOOTH_CONNECT" to Metadata(
            R.string.app_op_label_bluetooth_connect,
            PRIORITY_FILES_AND_DEVICES,
        ),
        "BLUETOOTH_ADVERTISE" to Metadata(
            R.string.app_op_label_bluetooth_advertise,
            PRIORITY_FILES_AND_DEVICES,
        ),
        "NEARBY_WIFI_DEVICES" to Metadata(
            R.string.app_op_label_nearby_wifi,
            PRIORITY_FILES_AND_DEVICES,
        ),
        "UWB_RANGING" to Metadata(
            R.string.app_op_label_uwb_ranging,
            PRIORITY_FILES_AND_DEVICES,
        ),
        "SYSTEM_ALERT_WINDOW" to Metadata(
            R.string.app_op_label_overlay,
            PRIORITY_SYSTEM_BEHAVIOR,
        ),
        "PICTURE_IN_PICTURE" to Metadata(
            R.string.app_op_label_picture_in_picture,
            PRIORITY_SYSTEM_BEHAVIOR,
        ),
        "RUN_IN_BACKGROUND" to Metadata(
            R.string.app_op_label_run_in_background,
            PRIORITY_SYSTEM_BEHAVIOR,
        ),
        "RUN_ANY_IN_BACKGROUND" to Metadata(
            R.string.app_op_label_run_any_in_background,
            PRIORITY_SYSTEM_BEHAVIOR,
        ),
        "START_FOREGROUND" to Metadata(
            R.string.app_op_label_start_foreground,
            PRIORITY_SYSTEM_BEHAVIOR,
        ),
        "WAKE_LOCK" to Metadata(
            R.string.app_op_label_wake_lock,
            PRIORITY_SYSTEM_BEHAVIOR,
        ),
        "VIBRATE" to Metadata(
            R.string.app_op_label_vibrate,
            PRIORITY_SYSTEM_BEHAVIOR,
        ),
        "ACTIVATE_VPN" to Metadata(
            R.string.app_op_label_activate_vpn,
            PRIORITY_SYSTEM_BEHAVIOR,
        ),
        "ESTABLISH_VPN_SERVICE" to Metadata(
            R.string.app_op_label_establish_vpn,
            PRIORITY_SYSTEM_BEHAVIOR,
        ),
        "REQUEST_INSTALL_PACKAGES" to Metadata(
            R.string.app_op_label_install_packages,
            PRIORITY_SYSTEM_BEHAVIOR,
        ),
        "SCHEDULE_EXACT_ALARM" to Metadata(
            R.string.app_op_label_exact_alarm,
            PRIORITY_SYSTEM_BEHAVIOR,
        ),
        "GET_USAGE_STATS" to Metadata(
            R.string.app_op_label_usage_stats,
            PRIORITY_SYSTEM_BEHAVIOR,
        ),
        "READ_DEVICE_IDENTIFIERS" to Metadata(
            R.string.app_op_label_device_identifiers,
            PRIORITY_SYSTEM_BEHAVIOR,
        ),
        "ACCESS_RESTRICTED_SETTINGS" to Metadata(
            R.string.app_op_label_restricted_settings,
            PRIORITY_SYSTEM_BEHAVIOR,
        ),
    )
}
