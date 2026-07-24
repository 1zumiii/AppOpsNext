package dev.izumi.appopsnext.presentation.history

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DailyHistoryCount(
    val date: LocalDate,
    val count: Int,
)

object HistoryStatistics {
    fun dailyCounts(
        events: List<ResolvedHistoryEvent>,
        nowMillis: Long,
        zoneId: ZoneId,
        dayCount: Int = DEFAULT_DAY_COUNT,
    ): List<DailyHistoryCount> {
        require(dayCount > 0) { "dayCount must be positive" }
        val today = Instant.ofEpochMilli(nowMillis)
            .atZone(zoneId)
            .toLocalDate()
        val firstDay = today.minusDays(dayCount.toLong() - 1)
        val countsByDate = events
            .map {
                Instant.ofEpochMilli(it.event.accessTimeMillis)
                    .atZone(zoneId)
                    .toLocalDate()
            }
            .filter { !it.isBefore(firstDay) && !it.isAfter(today) }
            .groupingBy { it }
            .eachCount()

        return (0 until dayCount).map { dayOffset ->
            val date = firstDay.plusDays(dayOffset.toLong())
            DailyHistoryCount(
                date = date,
                count = countsByDate[date] ?: 0,
            )
        }
    }

    private const val DEFAULT_DAY_COUNT = 7
}
