package dev.izumi.appopsnext.presentation.app_detail

import androidx.annotation.StringRes
import dev.izumi.appopsnext.R

internal object AppOpUsageDetailsFormatter {
    fun format(
        rawDetails: String?,
        stringResolver: (
            resourceId: Int,
            arguments: List<Any>,
        ) -> String,
    ): String? {
        if (rawDetails.isNullOrBlank()) return null

        val valuesByKey = rawDetails
            .split(DETAIL_SEPARATOR)
            .map(String::trim)
            .mapNotNull(::parseField)
            .toMap()

        val fragments = mutableListOf<String>()
        valuesByKey[LAST_USED_KEY]?.let { elapsedTime ->
            fragments += formatLastUsed(elapsedTime, stringResolver)
        }
        valuesByKey[DURATION_KEY]?.let { elapsedTime ->
            fragments += formatDuration(elapsedTime, stringResolver)
        }
        valuesByKey[LAST_REJECTED_KEY]?.let { elapsedTime ->
            fragments += formatLastRejected(elapsedTime, stringResolver)
        }

        return fragments
            .takeIf(List<String>::isNotEmpty)
            ?.joinToString(
                separator = stringResolver(
                    R.string.app_op_usage_separator,
                    emptyList(),
                ),
            )
    }

    private fun parseField(field: String): Pair<String, Long>? {
        val separatorIndex = field.indexOf(FIELD_SEPARATOR)
        if (separatorIndex <= 0) return null

        val key = field.substring(0, separatorIndex)
        if (key !in supportedKeys) return null

        val elapsedText = field
            .substring(separatorIndex + 1)
            .removePrefix("+")
            .removeSuffix(AGO_SUFFIX)
            .trim()
        val tokens = durationTokenPattern.findAll(elapsedText).toList()
        if (
            tokens.isEmpty() ||
            tokens.joinToString(separator = "") { it.value } != elapsedText
        ) {
            return null
        }

        val totalMilliseconds = runCatching {
            tokens.fold(0L) { total, token ->
                val value = token.groupValues[1].toLong()
                val unitMilliseconds = when (token.groupValues[2]) {
                    "d" -> MILLIS_PER_DAY
                    "h" -> MILLIS_PER_HOUR
                    "m" -> MILLIS_PER_MINUTE
                    "s" -> MILLIS_PER_SECOND
                    "ms" -> 1L
                    else -> error("Unsupported elapsed-time unit")
                }
                Math.addExact(
                    total,
                    Math.multiplyExact(value, unitMilliseconds),
                )
            }
        }.getOrNull() ?: return null

        return key to totalMilliseconds
    }

    private fun formatLastUsed(
        milliseconds: Long,
        stringResolver: (Int, List<Any>) -> String,
    ): String {
        val elapsed = formatElapsed(milliseconds, stringResolver)
            ?: return stringResolver(
                R.string.app_op_usage_just_now,
                emptyList(),
            )
        return stringResolver(R.string.app_op_usage_ago, listOf(elapsed))
    }

    private fun formatDuration(
        milliseconds: Long,
        stringResolver: (Int, List<Any>) -> String,
    ): String {
        val elapsed = formatElapsed(milliseconds, stringResolver)
            ?: return stringResolver(
                R.string.app_op_usage_duration_less_than_second,
                emptyList(),
            )
        return stringResolver(R.string.app_op_usage_duration, listOf(elapsed))
    }

    private fun formatLastRejected(
        milliseconds: Long,
        stringResolver: (Int, List<Any>) -> String,
    ): String {
        val elapsed = formatElapsed(milliseconds, stringResolver)
            ?: return stringResolver(
                R.string.app_op_usage_rejected_just_now,
                emptyList(),
            )
        return stringResolver(
            R.string.app_op_usage_rejected_ago,
            listOf(elapsed),
        )
    }

    private fun formatElapsed(
        milliseconds: Long,
        stringResolver: (Int, List<Any>) -> String,
    ): String? {
        var remainingSeconds = milliseconds / MILLIS_PER_SECOND
        if (remainingSeconds == 0L) return null

        val units = listOf(
            ElapsedUnit(
                seconds = SECONDS_PER_DAY,
                labelRes = R.string.app_op_usage_days,
            ),
            ElapsedUnit(
                seconds = SECONDS_PER_HOUR,
                labelRes = R.string.app_op_usage_hours,
            ),
            ElapsedUnit(
                seconds = SECONDS_PER_MINUTE,
                labelRes = R.string.app_op_usage_minutes,
            ),
            ElapsedUnit(
                seconds = 1L,
                labelRes = R.string.app_op_usage_seconds,
            ),
        )

        val parts = mutableListOf<String>()
        units.forEach { unit ->
            if (parts.size == MAX_DISPLAY_UNITS) return@forEach
            val value = remainingSeconds / unit.seconds
            if (value > 0) {
                parts += stringResolver(unit.labelRes, listOf(value))
                remainingSeconds %= unit.seconds
            }
        }
        return parts.joinToString(separator = " ")
    }

    private data class ElapsedUnit(
        val seconds: Long,
        @StringRes val labelRes: Int,
    )

    private const val DETAIL_SEPARATOR = ';'
    private const val FIELD_SEPARATOR = '='
    private const val AGO_SUFFIX = " ago"
    private const val LAST_USED_KEY = "time"
    private const val DURATION_KEY = "duration"
    private const val LAST_REJECTED_KEY = "rejectTime"
    private const val MAX_DISPLAY_UNITS = 2
    private const val MILLIS_PER_SECOND = 1_000L
    private const val MILLIS_PER_MINUTE = 60 * MILLIS_PER_SECOND
    private const val MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE
    private const val MILLIS_PER_DAY = 24 * MILLIS_PER_HOUR
    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 60 * SECONDS_PER_MINUTE
    private const val SECONDS_PER_DAY = 24 * SECONDS_PER_HOUR

    private val supportedKeys = setOf(
        LAST_USED_KEY,
        DURATION_KEY,
        LAST_REJECTED_KEY,
    )
    private val durationTokenPattern = Regex("""(\d+)(ms|d|h|m|s)""")
}
