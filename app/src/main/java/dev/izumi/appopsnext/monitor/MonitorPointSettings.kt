package dev.izumi.appopsnext.monitor

import dev.izumi.appopsnext.appops.model.AppOpNames

/** Which outcomes of a monitoring point are worth reporting. */
enum class MonitorOutcomes {
    ALL,

    /** Only attempts the system turned down, which is what a change is meant to cause. */
    REFUSED,

    /** Only accesses that went through. */
    ALLOWED,
    ;

    fun reports(allowed: Boolean): Boolean = when (this) {
        ALL -> true
        REFUSED -> !allowed
        ALLOWED -> allowed
    }
}

/**
 * How one monitoring point is reported.
 *
 * A monitoring point is a package and an operation together, and the three
 * settings here are the questions the monitor cannot answer for itself. How
 * often a point is worth hearing about depends on what the application does with
 * it: a location request is noted once per delivered fix, so a map application
 * fills the list in seconds, while a clipboard read matters every time. How
 * loudly depends on what the permission is: a camera opening in the background
 * is worth an interruption where a clipboard read may not be. And which outcomes
 * matter depends on why the point is being watched at all: someone who has just
 * denied a permission wants the refusals, and someone hunting for quiet use
 * wants everything that got through.
 *
 * A point with no entry is reported every time, on the global notification
 * setting, whatever the outcome.
 */
data class MonitorPointSettings(
    val packageName: String,
    val operationName: String,
    /** The shortest gap between two reports, or null to report every access. */
    val throttleSeconds: Int? = null,
    /** Null follows the monitor's own notification setting. */
    val headsUp: Boolean? = null,
    val outcomes: MonitorOutcomes = MonitorOutcomes.ALL,
    /**
     * Report only while the application is not the one on screen.
     *
     * Whether use you can see is worth reporting has no general answer: a map
     * drawing your route is using location exactly as you asked, and the same
     * application doing it from behind a notification may not be. The platform's
     * own process state decides, and a foreground service counts as off screen,
     * because a service is not something you are looking at.
     */
    val backgroundOnly: Boolean = false,
) {
    val throttleMillis: Long? get() = throttleSeconds?.let { it * 1_000L }

    val isDefault: Boolean
        get() = throttleSeconds == null &&
            headsUp == null &&
            outcomes == MonitorOutcomes.ALL &&
            !backgroundOnly

    fun headsUp(default: Boolean): Boolean = headsUp ?: default
}

object MonitorThrottles {
    /** Anything shorter would report faster than the platform's own repeats. */
    const val MIN_SECONDS = 1

    /** A day, past which an interval says more about a typing slip than an intent. */
    const val MAX_SECONDS = 24 * 60 * 60

    /** Offered when the throttle is first switched on, so nothing starts at zero. */
    const val DEFAULT_SECONDS = 60

    fun isValid(seconds: Int): Boolean = seconds in MIN_SECONDS..MAX_SECONDS
}

/**
 * Stores one point per line, as `package operation field field...`.
 *
 * Each setting is written as `name=value` and left out when it is the default,
 * so a line only ever carries what was actually chosen and a setting added later
 * reads as its default on a line written before it existed. A bare number is how
 * an earlier build wrote the interval on its own.
 */
object MonitorPointSettingsCodec {
    private const val THROTTLE = "throttle"
    private const val HEADS_UP = "headsup"
    private const val OUTCOME = "outcome"
    private const val BACKGROUND = "background"
    private const val ON = "on"
    private const val OFF = "off"

    fun decode(value: String?): List<MonitorPointSettings> {
        if (value.isNullOrBlank()) return emptyList()
        return value
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .mapNotNull(::decodeLine)
            .filterNot(MonitorPointSettings::isDefault)
            .distinctBy { it.packageName to it.operationName }
            .toList()
    }

    private fun decodeLine(line: String): MonitorPointSettings? {
        val fields = line.split(' ').filter(String::isNotEmpty)
        if (fields.size < 3) return null
        val packageName = fields[0]
        val operationName = AppOpNames.stableName(fields[1])
        if (packageName.isEmpty() || operationName.isEmpty()) return null
        var settings = MonitorPointSettings(packageName, operationName)
        for (field in fields.drop(2)) {
            val name = field.substringBefore('=')
            val raw = field.substringAfter('=', missingDelimiterValue = field)
            settings = when {
                // An earlier build wrote the interval as a bare number.
                name == raw && raw.toIntOrNull() != null -> settings.withThrottle(raw)
                name == THROTTLE -> settings.withThrottle(raw)
                name == HEADS_UP && raw == ON -> settings.copy(headsUp = true)
                name == HEADS_UP && raw == OFF -> settings.copy(headsUp = false)
                name == BACKGROUND && raw == ON -> settings.copy(backgroundOnly = true)
                name == BACKGROUND && raw == OFF -> settings.copy(backgroundOnly = false)
                name == OUTCOME -> settings.copy(
                    outcomes = MonitorOutcomes.entries
                        .firstOrNull { it.name.equals(raw, ignoreCase = true) }
                        ?: MonitorOutcomes.ALL,
                )
                // An unreadable field is dropped rather than taken as a default,
                // so one bad setting cannot discard the rest of the line.
                else -> settings
            }
        }
        return settings
    }

    private fun MonitorPointSettings.withThrottle(raw: String): MonitorPointSettings {
        val seconds = raw.toIntOrNull() ?: return this
        return if (MonitorThrottles.isValid(seconds)) copy(throttleSeconds = seconds) else this
    }

    fun encode(settings: List<MonitorPointSettings>): String =
        settings
            .filterNot(MonitorPointSettings::isDefault)
            .joinToString(separator = "\n") { point ->
                buildList {
                    add(point.packageName)
                    add(point.operationName)
                    point.throttleSeconds
                        ?.takeIf(MonitorThrottles::isValid)
                        ?.let { add("$THROTTLE=$it") }
                    point.headsUp?.let { add("$HEADS_UP=${if (it) ON else OFF}") }
                    if (point.outcomes != MonitorOutcomes.ALL) {
                        add("$OUTCOME=${point.outcomes.name.lowercase()}")
                    }
                    if (point.backgroundOnly) add("$BACKGROUND=$ON")
                }.joinToString(" ")
            }
}
