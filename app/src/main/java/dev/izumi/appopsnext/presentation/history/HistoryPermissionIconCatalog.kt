package dev.izumi.appopsnext.presentation.history

import androidx.annotation.DrawableRes
import dev.izumi.appopsnext.R
import java.util.Locale

internal enum class HistoryPermissionTone { PRIMARY, SECONDARY, TERTIARY, NEUTRAL }

internal data class HistoryPermissionVisual(
    @DrawableRes val iconRes: Int,
    val tone: HistoryPermissionTone,
)

/**
 * Add a mapping here when a permission needs a distinct visual cue.
 *
 * Tones follow the sense involved rather than the individual permission:
 * images and video are primary, sound is tertiary, location is secondary and
 * everything else stays neutral. Read and write variants share one icon.
 */
internal object HistoryPermissionIconCatalog {
    private val visuals = mapOf(
        // Images and video
        "CAMERA" to visual(R.drawable.ic_ph_camera, HistoryPermissionTone.PRIMARY),
        "READ_MEDIA_IMAGES" to visual(R.drawable.ic_ph_image, HistoryPermissionTone.PRIMARY),
        "READ_MEDIA_VIDEO" to visual(R.drawable.ic_ph_film_strip, HistoryPermissionTone.PRIMARY),
        "READ_MEDIA_VISUAL_USER_SELECTED" to
            visual(R.drawable.ic_ph_images, HistoryPermissionTone.PRIMARY),

        // Sound
        "RECORD_AUDIO" to visual(R.drawable.ic_ph_microphone, HistoryPermissionTone.TERTIARY),
        "READ_MEDIA_AUDIO" to visual(R.drawable.ic_ph_music_notes, HistoryPermissionTone.TERTIARY),

        // Location
        "FINE_LOCATION" to visual(R.drawable.ic_ph_map_pin, HistoryPermissionTone.SECONDARY),
        "COARSE_LOCATION" to visual(R.drawable.ic_ph_map_pin, HistoryPermissionTone.SECONDARY),
        "ACCESS_MEDIA_LOCATION" to
            visual(R.drawable.ic_ph_map_pin_area, HistoryPermissionTone.SECONDARY),

        // Clipboard
        "READ_CLIPBOARD" to visual(R.drawable.ic_ph_clipboard_text),
        "WRITE_CLIPBOARD" to visual(R.drawable.ic_ph_clipboard_text),

        // Phone, call log and messages
        "READ_PHONE_STATE" to visual(R.drawable.ic_ph_phone),
        "READ_PHONE_NUMBERS" to visual(R.drawable.ic_ph_hash),
        "CALL_PHONE" to visual(R.drawable.ic_ph_phone_outgoing),
        "ANSWER_PHONE_CALLS" to visual(R.drawable.ic_ph_phone_incoming),
        "READ_CALL_LOG" to visual(R.drawable.ic_ph_phone_list),
        "WRITE_CALL_LOG" to visual(R.drawable.ic_ph_phone_list),
        "READ_SMS" to visual(R.drawable.ic_ph_chat_text),
        "SEND_SMS" to visual(R.drawable.ic_ph_chat_text),
        "RECEIVE_SMS" to visual(R.drawable.ic_ph_chat_text),
        "RECEIVE_MMS" to visual(R.drawable.ic_ph_chat_text),

        // Personal data
        "READ_CONTACTS" to visual(R.drawable.ic_ph_address_book),
        "WRITE_CONTACTS" to visual(R.drawable.ic_ph_address_book),
        "READ_CALENDAR" to visual(R.drawable.ic_ph_calendar_blank),
        "WRITE_CALENDAR" to visual(R.drawable.ic_ph_calendar_blank),
        "GET_ACCOUNTS" to visual(R.drawable.ic_ph_user_circle),
        "READ_DEVICE_IDENTIFIERS" to visual(R.drawable.ic_ph_identification_card),
        "READ_EXTERNAL_STORAGE" to visual(R.drawable.ic_ph_folder_simple),
        "WRITE_EXTERNAL_STORAGE" to visual(R.drawable.ic_ph_folder_simple),

        // Sensors and nearby devices
        "BODY_SENSORS" to visual(R.drawable.ic_ph_heartbeat),
        "ACTIVITY_RECOGNITION" to visual(R.drawable.ic_ph_person_simple_walk),
        "BLUETOOTH_SCAN" to visual(R.drawable.ic_ph_bluetooth),
        "BLUETOOTH_ADVERTISE" to visual(R.drawable.ic_ph_bluetooth),
        "BLUETOOTH_CONNECT" to visual(R.drawable.ic_ph_bluetooth_connected),
        "NEARBY_WIFI_DEVICES" to visual(R.drawable.ic_ph_wifi_high),
        "UWB_RANGING" to visual(R.drawable.ic_ph_broadcast),

        // System behaviour
        "POST_NOTIFICATION" to visual(R.drawable.ic_ph_bell),
        "SYSTEM_ALERT_WINDOW" to visual(R.drawable.ic_ph_app_window),
        "PICTURE_IN_PICTURE" to visual(R.drawable.ic_ph_picture_in_picture),
        "RUN_IN_BACKGROUND" to visual(R.drawable.ic_ph_cpu),
        "RUN_ANY_IN_BACKGROUND" to visual(R.drawable.ic_ph_cpu),
        "START_FOREGROUND" to visual(R.drawable.ic_ph_play_circle),
        "WAKE_LOCK" to visual(R.drawable.ic_ph_sun),
        "SCHEDULE_EXACT_ALARM" to visual(R.drawable.ic_ph_alarm),
        "VIBRATE" to visual(R.drawable.ic_ph_vibrate),
        "GET_USAGE_STATS" to visual(R.drawable.ic_ph_chart_bar),
        "REQUEST_INSTALL_PACKAGES" to visual(R.drawable.ic_ph_package),
        "ACCESS_RESTRICTED_SETTINGS" to visual(R.drawable.ic_ph_lock_key),
        "ACTIVATE_VPN" to visual(R.drawable.ic_ph_network),
        "ESTABLISH_VPN_SERVICE" to visual(R.drawable.ic_ph_network),
    )

    fun visualFor(operationName: String): HistoryPermissionVisual {
        val key = operationName.removePrefix("android:").uppercase(Locale.ROOT)
        return visuals[key] ?: visual(R.drawable.ic_ph_shield)
    }

    private fun visual(
        @DrawableRes iconRes: Int,
        tone: HistoryPermissionTone = HistoryPermissionTone.NEUTRAL,
    ) = HistoryPermissionVisual(iconRes, tone)
}
