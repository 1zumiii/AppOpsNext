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
            .sortedWith(
                compareByDescending<AppHistorySummary> {
                    it.accessCount
                }.thenByDescending {
                    it.rejectCount
                }.thenBy {
                    it.app.label.lowercase(Locale.ROOT)
                }.thenBy {
                    it.app.packageName
                },
            )
}
