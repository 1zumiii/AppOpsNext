package dev.izumi.appopsnext.diagnostics

import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Reads back what [DiagnosticLogFormatter] wrote and decides how lines are
 * kept: which tier an old line belongs to, how the two tiers are merged and
 * which repeated lines are folded into one.
 */
internal object DiagnosticLogLines {
    /** A line in memory, with the time it is ordered by. */
    data class Entry(
        val line: String,
        /** Null for a line without a readable time, which then sorts first. */
        val instant: Instant?,
        /** Breaks ties between lines of the same instant in the order they were written. */
        val sequence: Long,
    )

    /** How long identical lines keep folding into the first of them. */
    val FOLD_WINDOW: Duration = Duration.ofMinutes(5)

    fun timestampOf(line: String): Instant? {
        val prefix = line.substringBefore(' ', missingDelimiterValue = "")
        if (prefix.isEmpty()) return null
        return try {
            OffsetDateTime.parse(prefix, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
        } catch (unreadable: DateTimeParseException) {
            null
        }
    }

    fun levelOf(line: String): DiagnosticLogLevel? {
        val match = LEVEL_PATTERN.find(line) ?: return null
        return DiagnosticLogLevel.entries.firstOrNull { it.name == match.groupValues[1] }
    }

    /**
     * The tier of a stored line, where the file does not say. Files from before
     * the tiers held both in one, and of those only the level can be read back
     * with certainty, so warnings and errors are important and the rest routine.
     */
    fun storedTierOf(line: String): DiagnosticLogTier =
        when (levelOf(line)) {
            DiagnosticLogLevel.WARNING, DiagnosticLogLevel.ERROR -> DiagnosticLogTier.IMPORTANT
            else -> DiagnosticLogTier.ROUTINE
        }

    /**
     * Interleaves two tiers by time. Each tier is already in the order it was
     * written, which is kept even when the clock went back in between, so this
     * merges rather than sorts.
     */
    fun merge(first: List<Entry>, second: List<Entry>): List<Entry> {
        val merged = ArrayList<Entry>(first.size + second.size)
        var i = 0
        var j = 0
        while (i < first.size && j < second.size) {
            if (ORDER.compare(first[i], second[j]) <= 0) merged += first[i++] else merged += second[j++]
        }
        while (i < first.size) merged += first[i++]
        while (j < second.size) merged += second[j++]
        return merged
    }

    /** The same as [merge] for plain lines, as read from the files. */
    fun mergeLines(first: List<String>, second: List<String>): List<String> =
        merge(first.toEntries(0), second.toEntries(first.size.toLong())).map(Entry::line)

    fun List<String>.toEntries(firstSequence: Long): List<Entry> =
        mapIndexed { index, line -> Entry(line, timestampOf(line), firstSequence + index) }

    /**
     * Lines whose exact text field reports are searched for are never folded:
     * the access monitor's (published test instructions quote them) and the
     * write failures, whose fields are read one by one.
     */
    fun foldable(source: String, message: String): Boolean =
        source.trim() !in UNFOLDED_SOURCES && !message.trimStart().startsWith("MIUI")

    /**
     * What a folded line ends with. The first line's text stays in front
     * unchanged, so a search for it still finds the folded line.
     */
    fun foldSuffix(count: Int, last: OffsetDateTime): String =
        " (x$count, last ${last.format(LAST_TIME)})"

    private val ORDER = compareBy<Entry>({ it.instant ?: Instant.MIN }, { it.sequence })
    private val LEVEL_PATTERN = Regex("^\\S+ \\[(INFO|WARNING|ERROR)] \\[")
    private val LAST_TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val UNFOLDED_SOURCES = setOf("Monitor", "AppOpsWrite")
}
