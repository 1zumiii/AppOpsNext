package dev.izumi.appopsnext.presentation.history

import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.history.model.AppOpHistoryEvent
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryStatisticsTest {
    private val zoneId = ZoneId.of("Asia/Shanghai")
    private val app = InstalledApp(
        label = "Example",
        packageName = "com.example",
        uid = 10_123,
        isSystemApp = false,
    )

    @Test
    fun `daily counts include zero days and ignore older events`() {
        val now = LocalDate.of(2026, 7, 24)
            .atTime(12, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val events = listOf(
            event(LocalDate.of(2026, 7, 24)),
            event(LocalDate.of(2026, 7, 24)),
            event(LocalDate.of(2026, 7, 22)),
            event(LocalDate.of(2026, 7, 17)),
        )

        assertEquals(
            listOf(0, 0, 0, 0, 1, 0, 2),
            HistoryStatistics.dailyCounts(
                events = events,
                nowMillis = now,
                zoneId = zoneId,
            ).map(DailyHistoryCount::count),
        )
    }

    private fun event(date: LocalDate): ResolvedHistoryEvent =
        ResolvedHistoryEvent(
            event = AppOpHistoryEvent(
                uid = app.uid,
                packageName = app.packageName,
                operationName = "CAMERA",
                attributionTag = null,
                accessTimeMillis = date
                    .atTime(10, 0)
                    .atZone(zoneId)
                    .toInstant()
                    .toEpochMilli(),
                durationMillis = null,
                uidState = "top",
                flags = "s",
            ),
            app = app,
        )
}
