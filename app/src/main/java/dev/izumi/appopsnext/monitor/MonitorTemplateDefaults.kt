package dev.izumi.appopsnext.monitor

/**
 * The templates that ship with the app, one for each configuration example that
 * names its permissions.
 *
 * They are read-only and never stored, so their ids are fixed and carry a prefix
 * no generated id has. Their names here are only a fallback: the screen shows
 * each one under the title of the example it came from.
 */
object BuiltInMonitorTemplates {
    private const val PREFIX = "builtin:"
    const val OWN_USE_LOCATION = "${PREFIX}own_use_location"
    const val QUIET_LOCATION = "${PREFIX}quiet_location"
    const val CAMERA_MICROPHONE = "${PREFIX}camera_microphone"
    const val PERSONAL_DATA_READS = "${PREFIX}personal_data_reads"

    private const val FINE_LOCATION = "android:fine_location"
    private const val COARSE_LOCATION = "android:coarse_location"
    private const val ONE_MINUTE = 60

    val all: List<MonitorTemplate> = listOf(
        // Location use the user asked for is expected, so only off-screen use is
        // reported, grouped by the minute, and without interrupting.
        MonitorTemplate(
            id = OWN_USE_LOCATION,
            name = "Location apps you use often",
            rules = listOf(FINE_LOCATION, COARSE_LOCATION).map { operation ->
                MonitorTemplateRule(
                    operationName = operation,
                    throttleSeconds = ONE_MINUTE,
                    headsUp = false,
                    backgroundOnly = true,
                )
            },
        ),
        // The same points as above, but the point of watching is to notice.
        MonitorTemplate(
            id = QUIET_LOCATION,
            name = "Suspected background location",
            rules = listOf(FINE_LOCATION, COARSE_LOCATION).map { operation ->
                MonitorTemplateRule(
                    operationName = operation,
                    throttleSeconds = ONE_MINUTE,
                    headsUp = true,
                    backgroundOnly = true,
                )
            },
        ),
        // Off-screen sensor use is rare enough that every occurrence is worth a pop-up.
        MonitorTemplate(
            id = CAMERA_MICROPHONE,
            name = "Camera or microphone turned on unexpectedly",
            rules = listOf("android:camera", "android:record_audio").map { operation ->
                MonitorTemplateRule(
                    operationName = operation,
                    headsUp = true,
                    backgroundOnly = true,
                )
            },
        ),
        // One-off reads matter each time and also while the app is on screen. How
        // loudly is left to the monitor's own setting, which the example names none of.
        MonitorTemplate(
            id = PERSONAL_DATA_READS,
            name = "Clipboard, contacts, SMS and call log",
            rules = listOf(
                "android:read_clipboard",
                "android:read_contacts",
                "android:read_sms",
                "android:read_call_log",
            ).map(::MonitorTemplateRule),
        ),
    )

    fun isBuiltIn(id: String): Boolean = id.startsWith(PREFIX)

    fun find(id: String): MonitorTemplate? = all.firstOrNull { it.id == id }
}
