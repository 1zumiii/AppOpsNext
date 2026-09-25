package dev.izumi.appopsnext.presentation.history

import dev.izumi.appopsnext.apps.model.InstalledApp
import java.util.Locale

data class AppHistorySummary(
    val app: InstalledApp,
    val accessCount: Int,
    val latestAccessTimeMillis: Long,
    val rejectCount: Int = 0,
    /** Interval accesses kept apart from individual records; see [PermissionHistory.recordCount]. */
    val intervalAccessCount: Int = 0,
)

enum class HistoryAppSort {
    TOTAL_ACTIVITY,
    ACCESSES,
    DENIALS,
    RECENT,
}

object HistoryAppStatistics {
    fun summarize(
        events: List<ResolvedHistoryEvent>,
        individualRecordsAvailable: Boolean = false,
    ): List<AppHistorySummary> =
        events
            .groupBy { it.app.packageName }
            .mapNotNull { (_, appEvents) ->
                val first = appEvents.firstOrNull()
                    ?: return@mapNotNull null
                AppHistorySummary(
                    app = first.app,
                    accessCount = appEvents
                        .filter { !individualRecordsAvailable || !it.event.isAggregated }
                        .sumOf { it.event.accessCount },
                    intervalAccessCount = if (individualRecordsAvailable) {
                        appEvents.filter { it.event.isAggregated }.sumOf { it.event.accessCount }
                    } else {
                        0
                    },
                    latestAccessTimeMillis = appEvents.maxOf {
                        it.event.accessTimeMillis
                    },
                    rejectCount = appEvents.sumOf { it.event.rejectCount },
                )
            }
            .let { sort(it, HistoryAppSort.TOTAL_ACTIVITY) }

    fun sort(summaries: List<AppHistorySummary>, order: HistoryAppSort): List<AppHistorySummary> {
        val comparator = when (order) {
            HistoryAppSort.TOTAL_ACTIVITY -> compareByDescending<AppHistorySummary> {
                it.accessCount.toLong() + it.rejectCount
            }
            HistoryAppSort.ACCESSES -> compareByDescending<AppHistorySummary> { it.accessCount }
            HistoryAppSort.DENIALS -> compareByDescending<AppHistorySummary> { it.rejectCount }
            HistoryAppSort.RECENT -> compareByDescending<AppHistorySummary> { it.latestAccessTimeMillis }
        }
        return summaries.sortedWith(
            comparator.thenBy { it.app.label.lowercase(Locale.ROOT) }
                .thenBy { it.app.packageName },
        )
    }
}
