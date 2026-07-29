package dev.izumi.appopsnext.presentation.history

import dev.izumi.appopsnext.apps.model.InstalledApp
import java.util.Locale

data class AppHistorySummary(
    val app: InstalledApp,
    val accessCount: Int,
    val latestAccessTimeMillis: Long,
)

object HistoryAppStatistics {
    fun summarize(
        events: List<ResolvedHistoryEvent>,
    ): List<AppHistorySummary> =
        events
            .groupBy { it.app.packageName }
            .mapNotNull { (_, appEvents) ->
                val first = appEvents.firstOrNull()
                    ?: return@mapNotNull null
                AppHistorySummary(
                    app = first.app,
                    accessCount = appEvents.sumOf {
                        it.event.accessCount
                    },
                    latestAccessTimeMillis = appEvents.maxOf {
                        it.event.accessTimeMillis
                    },
                )
            }
            .sortedWith(
                compareByDescending<AppHistorySummary> {
                    it.accessCount
                }.thenBy {
                    it.app.label.lowercase(Locale.ROOT)
                }.thenBy {
                    it.app.packageName
                },
            )
}
