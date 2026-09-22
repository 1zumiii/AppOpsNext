package dev.izumi.appopsnext.presentation.history

import dev.izumi.appopsnext.history.AppOpsHistoryRepository
import java.time.Instant
import java.time.ZoneId

/** Calendar-day ranges, so a range lines up with the bars of the daily chart. */
enum class HistoryTimeRange(val days: Int?) {
    TODAY(1),
    WEEK(AppOpsHistoryRepository.INDIVIDUAL_RECORD_RETENTION_DAYS),
    MONTH(AppOpsHistoryRepository.HISTORY_WINDOW_DAYS),
    /** Everything saved; offered only while individual records are being saved. */
    ALL(null),
    ;

    companion object {
        fun available(savingIndividualRecords: Boolean): List<HistoryTimeRange> =
            if (savingIndividualRecords) entries else entries - ALL
    }
}

object HistoryFilter {
    fun rangeStartMillis(
        range: HistoryTimeRange,
        nowMillis: Long,
        zoneId: ZoneId,
    ): Long {
        val days = range.days ?: return Long.MIN_VALUE
        return Instant.ofEpochMilli(nowMillis)
            .atZone(zoneId)
            .toLocalDate()
            .minusDays(days - 1L)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    /**
     * An interval belongs to the range its end falls in, the way the chart files
     * it, so one that starts before the range is still shown whole with its bounds.
     */
    fun apply(
        history: PermissionHistory,
        range: HistoryTimeRange,
        packageName: String?,
        nowMillis: Long,
        zoneId: ZoneId,
    ): PermissionHistory {
        val start = rangeStartMillis(range, nowMillis, zoneId)
        return history.copy(
            events = history.events.filter {
                it.event.accessTimeMillis >= start &&
                    (packageName == null || it.app.packageName == packageName)
            },
        )
    }

    /** Accesses only: a denied attempt is counted in the summary, never listed. */
    fun timeline(events: List<ResolvedHistoryEvent>): HistoryTimeline {
        val accesses = events.filter { it.event.accessCount > 0 }
        return HistoryTimeline(
            individual = accesses.filterNot { it.event.isAggregated },
            intervals = accesses.filter { it.event.isAggregated },
        )
    }
}

data class HistoryTimeline(
    val individual: List<ResolvedHistoryEvent>,
    val intervals: List<ResolvedHistoryEvent>,
) {
    val isEmpty: Boolean get() = individual.isEmpty() && intervals.isEmpty()
}
