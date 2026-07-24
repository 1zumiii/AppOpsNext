package dev.izumi.appopsnext.history.parser

import dev.izumi.appopsnext.history.model.AppOpHistoryEvent
import java.text.SimpleDateFormat
import java.util.Locale

class DiscreteAppOpsHistoryParser {
    fun parse(
        operationName: String,
        output: String,
    ): List<AppOpHistoryEvent> {
        val dateFormat = SimpleDateFormat(DATE_PATTERN, Locale.US).apply {
            isLenient = false
        }
        val events = mutableListOf<AppOpHistoryEvent>()
        var readingDiscreteHistory = false
        var uid: Int? = null
        var packageName: String? = null
        var currentOperation: String? = null
        var attributionTag: String? = null

        output.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (!readingDiscreteHistory) {
                readingDiscreteHistory = line == DISCRETE_SECTION_HEADER
                return@forEach
            }

            UID_PATTERN.matchEntire(line)?.let { match ->
                uid = match.groupValues[1].toIntOrNull()
                packageName = null
                currentOperation = null
                attributionTag = null
                return@forEach
            }
            PACKAGE_PATTERN.matchEntire(line)?.let { match ->
                packageName = match.groupValues[1]
                currentOperation = null
                attributionTag = null
                return@forEach
            }
            if (line == operationName) {
                currentOperation = line
                attributionTag = null
                return@forEach
            }
            ATTRIBUTION_PATTERN.matchEntire(line)?.let { match ->
                attributionTag = match.groupValues[1]
                    .takeUnless { it == NULL_ATTRIBUTION }
                return@forEach
            }

            val access = ACCESS_PATTERN.matchEntire(line) ?: return@forEach
            val parsedUid = uid ?: return@forEach
            val parsedPackage = packageName ?: return@forEach
            val parsedOperation = currentOperation ?: return@forEach
            val accessTime = dateFormat
                .parse(access.groupValues[2])
                ?.time
                ?: return@forEach
            val stateAndFlags = access.groupValues[1]
            val separatorIndex = stateAndFlags.indexOf('-')
            val duration = access.groupValues[3]
                .takeIf(String::isNotEmpty)
                ?.toLongOrNull()

            events += AppOpHistoryEvent(
                uid = parsedUid,
                packageName = parsedPackage,
                operationName = parsedOperation,
                attributionTag = attributionTag,
                accessTimeMillis = accessTime,
                durationMillis = duration,
                uidState = if (separatorIndex > 0) {
                    stateAndFlags.substring(0, separatorIndex)
                } else {
                    stateAndFlags
                },
                flags = if (separatorIndex > 0) {
                    stateAndFlags.substring(separatorIndex + 1)
                } else {
                    ""
                },
            )
        }

        return events.sortedByDescending(AppOpHistoryEvent::accessTimeMillis)
    }

    private companion object {
        const val DISCRETE_SECTION_HEADER = "Discrete accesses:"
        const val NULL_ATTRIBUTION = "null"
        const val DATE_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS"

        val UID_PATTERN = Regex("""Uid:\s+(\d+)""")
        val PACKAGE_PATTERN = Regex("""Package:\s+(\S+)""")
        val ATTRIBUTION_PATTERN = Regex("""Attribution:\s+(.+)""")
        val ACCESS_PATTERN = Regex(
            """Access \[([^\]]+)] at """ +
                """(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})""" +
                """(?: for (\d+) milliseconds)?(?:\s+.*)?""",
        )
    }
}
