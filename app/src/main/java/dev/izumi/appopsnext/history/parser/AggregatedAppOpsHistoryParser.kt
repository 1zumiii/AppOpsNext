package dev.izumi.appopsnext.history.parser

import dev.izumi.appopsnext.history.AppOpsHistoryOutputExtractor
import dev.izumi.appopsnext.history.model.AppOpHistoryEvent
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Parses Android's time-bucketed AppOps snapshots. These snapshots are the
 * only history available for operations that the system does not retain as
 * individual discrete accesses.
 */
class AggregatedAppOpsHistoryParser {
    fun parse(
        operationName: String,
        output: String,
    ): List<AppOpHistoryEvent> {
        val dateFormat = SimpleDateFormat(DATE_PATTERN, Locale.US).apply {
            isLenient = false
        }
        val events = mutableListOf<AppOpHistoryEvent>()
        var readingAggregatedHistory = false
        var beginTimeMillis: Long? = null
        var endTimeMillis: Long? = null
        var uid: Int? = null
        var packageName: String? = null
        var attributionTag: String? = null
        var currentOperation: String? = null

        output.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (!readingAggregatedHistory) {
                readingAggregatedHistory =
                    line == AppOpsHistoryOutputExtractor
                        .AGGREGATED_SECTION_HEADER
                return@forEach
            }
            if (line == DISCRETE_SECTION_HEADER) {
                return events.sortedByDescending(
                    AppOpHistoryEvent::accessTimeMillis,
                )
            }

            when {
                line == SNAPSHOT_HEADER -> {
                    beginTimeMillis = null
                    endTimeMillis = null
                    uid = null
                    packageName = null
                    attributionTag = null
                    currentOperation = null
                }

                line.startsWith(BEGIN_PREFIX) -> {
                    beginTimeMillis = parseTimestamp(
                        value = line.removePrefix(BEGIN_PREFIX),
                        dateFormat = dateFormat,
                    )
                }

                line.startsWith(END_PREFIX) -> {
                    endTimeMillis = parseTimestamp(
                        value = line.removePrefix(END_PREFIX),
                        dateFormat = dateFormat,
                    )
                }

                UID_PATTERN.matches(line) -> {
                    uid = parseUid(
                        UID_PATTERN.matchEntire(line)
                            ?.groupValues
                            ?.get(1)
                            .orEmpty(),
                    )
                    packageName = null
                    attributionTag = null
                    currentOperation = null
                }

                PACKAGE_PATTERN.matches(line) -> {
                    packageName = PACKAGE_PATTERN
                        .matchEntire(line)
                        ?.groupValues
                        ?.get(1)
                    attributionTag = null
                    currentOperation = null
                }

                ATTRIBUTION_PATTERN.matches(line) -> {
                    attributionTag = ATTRIBUTION_PATTERN
                        .matchEntire(line)
                        ?.groupValues
                        ?.get(1)
                        ?.takeUnless { it == NULL_ATTRIBUTION }
                    currentOperation = null
                }

                OPERATION_PATTERN.matches(line) -> {
                    currentOperation = line.removeSuffix(":")
                }

                currentOperation == operationName -> {
                    val value = VALUE_PATTERN.matchEntire(line)
                        ?: return@forEach
                    val parsedUid = uid ?: return@forEach
                    val parsedPackage = packageName ?: return@forEach
                    val begin = beginTimeMillis ?: return@forEach
                    val end = endTimeMillis
                        ?.takeIf { it >= begin }
                        ?: begin
                    val metrics = value.groupValues[2]
                    val accessCount = ACCESS_COUNT_PATTERN
                        .find(metrics)
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull()
                    val duration = DURATION_PATTERN
                        .find(metrics)
                        ?.groupValues
                        ?.get(1)
                        ?.let(::parseDurationMillis)
                    if (accessCount == null && duration == null) {
                        return@forEach
                    }
                    val stateAndFlags = value.groupValues[1]
                    val separatorIndex = stateAndFlags.indexOf('-')

                    events += AppOpHistoryEvent(
                        uid = parsedUid,
                        packageName = parsedPackage,
                        operationName = operationName,
                        attributionTag = attributionTag,
                        accessTimeMillis = end,
                        durationMillis = duration,
                        uidState = stateAndFlags.substring(
                            startIndex = 0,
                            endIndex = separatorIndex
                                .takeIf { it > 0 }
                                ?: stateAndFlags.length,
                        ),
                        flags = if (separatorIndex > 0) {
                            stateAndFlags.substring(separatorIndex + 1)
                        } else {
                            ""
                        },
                        accessCount = accessCount ?: 1,
                        isAggregated = true,
                    )
                }
            }
        }

        return events.sortedByDescending(
            AppOpHistoryEvent::accessTimeMillis,
        )
    }

    private fun parseTimestamp(
        value: String,
        dateFormat: SimpleDateFormat,
    ): Long? = DATE_VALUE_PATTERN
        .find(value)
        ?.value
        ?.let(dateFormat::parse)
        ?.time

    private fun parseUid(value: String): Int? {
        value.toIntOrNull()?.let { return it }
        val match = APP_UID_PATTERN.matchEntire(value) ?: return null
        val userId = match.groupValues[1].toIntOrNull() ?: return null
        val appId = match.groupValues[2].toIntOrNull() ?: return null
        return userId * PER_USER_RANGE + FIRST_APPLICATION_UID + appId
    }

    private fun parseDurationMillis(value: String): Long? {
        val match = DURATION_VALUE_PATTERN.matchEntire(value) ?: return null
        val days = match.groupValues[1].toLongOrNull() ?: 0L
        val hours = match.groupValues[2].toLongOrNull() ?: 0L
        val minutes = match.groupValues[3].toLongOrNull() ?: 0L
        val seconds = match.groupValues[4].toLongOrNull() ?: 0L
        val milliseconds = match.groupValues[5].toLongOrNull() ?: 0L
        return days * MILLIS_PER_DAY +
            hours * MILLIS_PER_HOUR +
            minutes * MILLIS_PER_MINUTE +
            seconds * MILLIS_PER_SECOND +
            milliseconds
    }

    private companion object {
        const val DISCRETE_SECTION_HEADER = "Discrete accesses:"
        const val SNAPSHOT_HEADER = "snapshot:"
        const val BEGIN_PREFIX = "begin = "
        const val END_PREFIX = "end = "
        const val NULL_ATTRIBUTION = "null"
        const val DATE_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS"
        const val PER_USER_RANGE = 100_000
        const val FIRST_APPLICATION_UID = 10_000
        const val MILLIS_PER_SECOND = 1_000L
        const val MILLIS_PER_MINUTE = 60L * MILLIS_PER_SECOND
        const val MILLIS_PER_HOUR = 60L * MILLIS_PER_MINUTE
        const val MILLIS_PER_DAY = 24L * MILLIS_PER_HOUR

        val DATE_VALUE_PATTERN =
            Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}""")
        val UID_PATTERN = Regex("""Uid\s+([^:]+):""")
        val APP_UID_PATTERN = Regex("""u(\d+)a(\d+)""")
        val PACKAGE_PATTERN = Regex("""Package\s+([^:]+):""")
        val ATTRIBUTION_PATTERN = Regex("""Attribution\s+(.+):""")
        val OPERATION_PATTERN = Regex("""[A-Z][A-Z0-9_]*:""")
        val VALUE_PATTERN = Regex("""\[([^\]]+)]\s*=\s*(.+)""")
        val ACCESS_COUNT_PATTERN = Regex("""(?:^|,\s*)access=(\d+)""")
        val DURATION_PATTERN = Regex("""(?:^|,\s*)duration=(\+\S+)""")
        val DURATION_VALUE_PATTERN = Regex(
            """\+(?:(\d+)d)?(?:(\d+)h)?(?:(\d+)m)?""" +
                """(?:(\d+)s)?(?:(\d+)ms)?""",
        )
    }
}
