package dev.izumi.appopsnext.presentation.settings

import java.time.LocalDate
import java.time.ZoneId

/** Whole local days, both ends included. */
data class SavedHistoryDateRange(
    val start: LocalDate,
    val end: LocalDate,
) {
    /** [from, to) in epoch milliseconds. */
    fun millis(zoneId: ZoneId): Pair<Long, Long> =
        start.atStartOfDay(zoneId).toInstant().toEpochMilli() to
            end.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

    /** Moving the start past the end brings the end along, never the other way round. */
    fun withStart(date: LocalDate, earliest: LocalDate, latest: LocalDate): SavedHistoryDateRange {
        val clamped = date.coerceIn(earliest, latest)
        return SavedHistoryDateRange(clamped, maxOf(end.coerceIn(earliest, latest), clamped))
    }

    fun withEnd(date: LocalDate, earliest: LocalDate, latest: LocalDate): SavedHistoryDateRange {
        val clamped = date.coerceIn(earliest, latest)
        return SavedHistoryDateRange(minOf(start.coerceIn(earliest, latest), clamped), clamped)
    }

    /** Keeps a range chosen earlier valid after records were deleted or added. */
    fun clampedTo(earliest: LocalDate, latest: LocalDate): SavedHistoryDateRange =
        withStart(start, earliest, latest)
}
