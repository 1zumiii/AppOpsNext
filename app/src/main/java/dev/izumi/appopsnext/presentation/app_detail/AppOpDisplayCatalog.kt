package dev.izumi.appopsnext.presentation.app_detail

import androidx.annotation.StringRes
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.appops.model.AppOpEntry
import dev.izumi.appopsnext.appops.model.AppOpNames
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.appops.model.MiuiOps

data class AppOpDisplayItem(
    val operationName: String,
    @StringRes val labelRes: Int?,
    val mode: String,
    val details: String?,
    val scope: AppOpScope,
    val priority: Int,
    val isImplicitDefault: Boolean,
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
        /** Shell names the device defines beyond this catalog, listed as default rows. */
        extraOperations: List<String> = emptyList(),
    ): List<AppOpDisplayItem> {
        val normalizedQuery = query.trim()
        val explicitItems = entries
            .groupBy { normalize(it.name) }
            .mapValues { (_, scopedEntries) ->
                val effectiveEntry = scopedEntries
                    .firstOrNull { it.scope == AppOpScope.UID }
                    ?: scopedEntries.first()
                val metadata = metadataOf(effectiveEntry.name)
                AppOpDisplayItem(
                    operationName = effectiveEntry.name,
                    labelRes = metadata?.labelRes,
                    mode = effectiveEntry.mode,
                    details = effectiveEntry.details
                        ?: scopedEntries.firstNotNullOfOrNull(AppOpEntry::details),
                    scope = effectiveEntry.scope,
                    priority = metadata?.priority ?: PRIORITY_OTHER,
                    isImplicitDefault = false,
                )
            }
        // Labels come from resources, so each one is resolved once here instead of
        // once per filter test and once per sort comparison.
        return metadataByOperation
            .map { (operationName, metadata) ->
                explicitItems[operationName] ?: AppOpDisplayItem(
                    operationName = operationName,
                    labelRes = metadata.labelRes,
                    mode = "default",
                    details = null,
                    scope = AppOpScope.PACKAGE,
                    priority = metadata.priority,
                    isImplicitDefault = true,
                )
            }
            .plus(
                explicitItems
                    .filterKeys { it !in metadataByOperation }
                    .values,
            )
            .plus(
                extraOperations
                    .filter { normalize(it) !in metadataByOperation && normalize(it) !in explicitItems }
                    .distinctBy(::normalize)
                    .map { operationName ->
                        AppOpDisplayItem(
                            operationName = operationName,
                            labelRes = null,
                            mode = "default",
                            details = null,
                            scope = AppOpScope.PACKAGE,
                            priority = PRIORITY_OTHER,
                            isImplicitDefault = true,
                        )
                    },
            )
            .map { item ->
                LabelledAppOp(
                    item = item,
                    label = item.labelRes?.let(labelResolver),
                    alternateLabel = if (normalizedQuery.isEmpty()) {
                        null
                    } else {
                        item.labelRes?.let(alternateLabelResolver)
                    },
                )
            }
            .filter { labelled ->
                normalizedQuery.isEmpty() ||
                    labelled.item.operationName.contains(
                        normalizedQuery,
                        ignoreCase = true,
                    ) ||
                    labelled.label?.contains(
                        normalizedQuery,
                        ignoreCase = true,
                    ) == true ||
                    labelled.alternateLabel?.contains(
                        normalizedQuery,
                        ignoreCase = true,
                    ) == true
            }
            .sortedWith(
                compareBy<LabelledAppOp> { it.item.priority }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { labelled ->
                        labelled.label ?: labelled.item.operationName
                    }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) {
                        it.item.operationName
                    },
            )
            .map(LabelledAppOp::item)
    }

    private data class LabelledAppOp(
        val item: AppOpDisplayItem,
        val label: String?,
        val alternateLabel: String?,
    )

    /** The localized label for an operation, or null when it is not catalogued. */
    fun labelResOf(operationName: String): Int? =
        metadataOf(operationName)?.labelRes

    private fun metadataOf(operationName: String): Metadata? =
        metadataByOperation[normalize(operationName)]
            ?: MiuiOps.codeOf(operationName)?.let { code ->
                miuiLabels[code]?.let { Metadata(it, PRIORITY_MIUI) }
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
    private const val PRIORITY_MIUI = 90
    private const val PRIORITY_OTHER = 100

    /**
     * Labels for the MIUI operations the framework names in `MiuiHooks`. They
     * label rows the device reports and add no default rows, since other
     * devices lack these operations. The framework carries no descriptions, so
     * each label is read from its constant name.
     */
    private val miuiLabels = mapOf(
        10001 to R.string.miui_op_label_wifi_change,
        10002 to R.string.miui_op_label_bluetooth_change,
        10003 to R.string.miui_op_label_data_connect_change,
        10004 to R.string.miui_op_label_send_mms,
        10005 to R.string.miui_op_label_read_mms,
        10006 to R.string.miui_op_label_write_mms,
        10007 to R.string.miui_op_label_boot_completed,
        10008 to R.string.miui_op_label_auto_start,
        10009 to R.string.miui_op_label_nfc_change,
        10010 to R.string.miui_op_label_delete_sms,
        10011 to R.string.miui_op_label_delete_mms,
        10012 to R.string.miui_op_label_delete_contacts,
        10013 to R.string.miui_op_label_delete_call_log,
        10014 to R.string.miui_op_label_exact_alarm,
        10015 to R.string.miui_op_label_access_xiaomi_account,
        10016 to R.string.miui_op_label_nfc,
        10017 to R.string.miui_op_label_install_shortcut,
        10018 to R.string.miui_op_label_read_notification_sms,
        10019 to R.string.miui_op_label_get_tasks,
        10020 to R.string.miui_op_label_show_when_locked,
        10021 to R.string.miui_op_label_background_start_activity,
        10022 to R.string.miui_op_label_get_installed_apps,
        10023 to R.string.miui_op_label_service_foreground,
        10024 to R.string.miui_op_label_get_anonymous_id,
        10025 to R.string.miui_op_label_get_udevice_id,
        10026 to R.string.miui_op_label_show_daemon_notification,
        10027 to R.string.miui_op_label_background_location,
        10028 to R.string.miui_op_label_read_sms_real,
        10029 to R.string.miui_op_label_read_contacts_real,
        10030 to R.string.miui_op_label_read_calendar_real,
        10031 to R.string.miui_op_label_read_call_log_real,
        10032 to R.string.miui_op_label_read_phone_state_real,
        10033 to R.string.miui_op_label_post_local_notification,
        10034 to R.string.miui_op_label_access_gallery,
        10035 to R.string.miui_op_label_access_sociality,
    )

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
