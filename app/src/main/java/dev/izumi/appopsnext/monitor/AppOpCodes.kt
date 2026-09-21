package dev.izumi.appopsnext.monitor

import dev.izumi.appopsnext.appops.model.AppOpNames

/**
 * AppOps operation codes, in the order AOSP declares them in
 * `AppOpsManager.sAppOpInfos`, where a row's index is its operation code.
 *
 * The watch interfaces take codes rather than names, so this table is the bridge
 * to the shell names used everywhere else. The list is append-only upstream
 * because the codes are persisted, and it was identical in API 35 and 36.
 * A wrong entry surfaces through the self-check rather than failing silently.
 */
internal object AppOpCodes {
    private val codeToName: Map<Int, String> = mapOf(
        0 to "android:coarse_location",
        1 to "android:fine_location",
        2 to "android:gps",
        3 to "android:vibrate",
        4 to "android:read_contacts",
        5 to "android:write_contacts",
        6 to "android:read_call_log",
        7 to "android:write_call_log",
        8 to "android:read_calendar",
        9 to "android:write_calendar",
        10 to "android:wifi_scan",
        11 to "android:post_notification",
        12 to "android:neighboring_cells",
        13 to "android:call_phone",
        14 to "android:read_sms",
        15 to "android:write_sms",
        16 to "android:receive_sms",
        17 to "android:receive_emergency_broadcast",
        18 to "android:receive_mms",
        19 to "android:receive_wap_push",
        20 to "android:send_sms",
        21 to "android:read_icc_sms",
        22 to "android:write_icc_sms",
        23 to "android:write_settings",
        24 to "android:system_alert_window",
        25 to "android:access_notifications",
        26 to "android:camera",
        27 to "android:record_audio",
        28 to "android:play_audio",
        29 to "android:read_clipboard",
        30 to "android:write_clipboard",
        31 to "android:take_media_buttons",
        32 to "android:take_audio_focus",
        33 to "android:audio_master_volume",
        34 to "android:audio_voice_volume",
        35 to "android:audio_ring_volume",
        36 to "android:audio_media_volume",
        37 to "android:audio_alarm_volume",
        38 to "android:audio_notification_volume",
        39 to "android:audio_bluetooth_volume",
        40 to "android:wake_lock",
        41 to "android:monitor_location",
        42 to "android:monitor_location_high_power",
        43 to "android:get_usage_stats",
        44 to "android:mute_microphone",
        45 to "android:toast_window",
        46 to "android:project_media",
        47 to "android:activate_vpn",
        48 to "android:write_wallpaper",
        49 to "android:assist_structure",
        50 to "android:assist_screenshot",
        51 to "android:read_phone_state",
        52 to "android:add_voicemail",
        53 to "android:use_sip",
        54 to "android:process_outgoing_calls",
        55 to "android:use_fingerprint",
        56 to "android:body_sensors",
        57 to "android:read_cell_broadcasts",
        58 to "android:mock_location",
        59 to "android:read_external_storage",
        60 to "android:write_external_storage",
        61 to "android:turn_screen_on",
        62 to "android:get_accounts",
        63 to "android:run_in_background",
        64 to "android:audio_accessibility_volume",
        65 to "android:read_phone_numbers",
        66 to "android:request_install_packages",
        67 to "android:picture_in_picture",
        68 to "android:instant_app_start_foreground",
        69 to "android:answer_phone_calls",
        70 to "android:run_any_in_background",
        71 to "android:change_wifi_state",
        72 to "android:request_delete_packages",
        73 to "android:bind_accessibility_service",
        74 to "android:accept_handover",
        75 to "android:manage_ipsec_tunnels",
        76 to "android:start_foreground",
        77 to "android:bluetooth_scan",
        78 to "android:use_biometric",
        79 to "android:activity_recognition",
        80 to "android:sms_financial_transactions",
        81 to "android:read_media_audio",
        82 to "android:write_media_audio",
        83 to "android:read_media_video",
        84 to "android:write_media_video",
        85 to "android:read_media_images",
        86 to "android:write_media_images",
        87 to "android:legacy_storage",
        88 to "android:access_accessibility",
        89 to "android:read_device_identifiers",
        90 to "android:access_media_location",
        91 to "android:query_all_packages",
        92 to "android:manage_external_storage",
        93 to "android:interact_across_profiles",
        94 to "android:activate_platform_vpn",
        95 to "android:loader_usage_stats",
        96 to "android:auto_revoke_permissions_if_unused",
        97 to "android:auto_revoke_managed_by_installer",
        98 to "android:no_isolated_storage",
        99 to "android:phone_call_microphone",
        100 to "android:phone_call_camera",
        101 to "android:record_audio_hotword",
        102 to "android:manage_ongoing_calls",
        103 to "android:manage_credentials",
        104 to "android:use_icc_auth_with_device_identifier",
        105 to "android:record_audio_output",
        106 to "android:schedule_exact_alarm",
        107 to "android:fine_location_source",
        108 to "android:coarse_location_source",
        109 to "android:manage_media",
        110 to "android:bluetooth_connect",
        111 to "android:uwb_ranging",
        112 to "android:activity_recognition_source",
        113 to "android:bluetooth_advertise",
        114 to "android:record_incoming_phone_audio",
        115 to "android:nearby_wifi_devices",
        116 to "android:establish_vpn_service",
        117 to "android:establish_vpn_manager",
        118 to "android:access_restricted_settings",
        119 to "android:receive_ambient_trigger_audio",
        120 to "android:receive_explicit_user_interaction_audio",
        121 to "android:run_user_initiated_jobs",
        122 to "android:read_media_visual_user_selected",
        123 to "android:system_exempt_from_suspension",
        124 to "android:system_exempt_from_dismissible_notifications",
        125 to "android:read_write_health_data",
        126 to "android:foreground_service_special_use",
        127 to "android:system_exempt_from_power_restrictions",
        128 to "android:system_exempt_from_hibernation",
        129 to "android:system_exempt_from_activity_bg_start_restriction",
        130 to "android:deprecated_2",
        131 to "android:use_full_screen_intent",
        132 to "android:camera_sandboxed",
        133 to "android:record_audio_sandboxed",
        134 to "android:receive_sandbox_trigger_audio",
        135 to "android:deprecated_3",
        136 to "android:create_accessibility_overlay",
        137 to "android:media_routing_control",
        138 to "android:enable_mobile_data_by_user",
        139 to "android:reserved_for_testing",
        140 to "android:rapid_clear_notifications_by_listener",
        141 to "android:read_system_grammatical_gender",
        142 to "android:deprecated_4",
        143 to "android:archive_icon_overlay",
        144 to "android:unarchival_support",
        145 to "android:emergency_location",
        146 to "android:receive_sensitive_notifications",
    )

    private val nameToCode: Map<String, Int> =
        codeToName.entries.associate { (code, name) -> name to code }

    /** Accepts either the `android:camera` or the bare `CAMERA` spelling. */
    fun codeOf(operationName: String): Int? =
        nameToCode[AppOpNames.stableName(operationName)]

    fun nameOf(code: Int): String? = codeToName[code]

    /**
     * Operations watched by the experimental monitor. Long-running sensor access
     * plus the one-shot personal-data reads that the system surfaces least.
     */
    val MONITORED_NAMES: List<String> = listOf(
        "android:camera",
        "android:record_audio",
        "android:coarse_location",
        "android:fine_location",
        "android:read_clipboard",
        "android:read_contacts",
        "android:read_sms",
        "android:read_call_log",
        "android:read_phone_state",
        "android:body_sensors",
    )

    /**
     * Every operation the table knows, for the picker's advanced list. Many of
     * them are never noted for an ordinary application, and some are reported so
     * often that they are only usable with an interval set.
     */
    val ALL_NAMES: List<String> = codeToName.values.sorted()

    val MONITORED_CODES: IntArray =
        MONITORED_NAMES.mapNotNull(::codeOf).toIntArray()

    /** The operation the self-check triggers on purpose. */
    const val SELF_CHECK_OP: String = "android:read_clipboard"
}
