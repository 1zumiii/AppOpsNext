package dev.izumi.appopsnext.presentation.history

import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.history.model.AppOpHistoryEvent
import dev.izumi.appopsnext.history.model.HistoryPermission
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryFilterTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = millis("2026-09-22T15:00")
    private val alpha = InstalledApp("Alpha", "com.example.alpha", 10_123, isSystemApp = false)
    private val beta = InstalledApp("Beta", "com.example.beta", 10_124, isSystemApp = false)

    @Test fun `ranges start at local midnight so they match the daily chart`() {
        assertEquals(millis("2026-09-22T00:00"), HistoryFilter.rangeStartMillis(HistoryTimeRange.TODAY, now, zone))
        assertEquals(millis("2026-09-16T00:00"), HistoryFilter.rangeStartMillis(HistoryTimeRange.WEEK, now, zone))
        assertEquals(millis("2026-08-24T00:00"), HistoryFilter.rangeStartMillis(HistoryTimeRange.MONTH, now, zone))
    }

    @Test fun `an interval belongs to the range its end falls in`() {
        val history = history(
            event(alpha, "2026-09-22T00:15", interval = "2026-09-21T23:45"),
            event(alpha, "2026-09-21T23:30"),
        )
        val today = HistoryFilter.apply(history, HistoryTimeRange.TODAY, null, now, zone)
        assertEquals(listOf(millis("2026-09-22T00:15")), today.events.map { it.event.accessTimeMillis })
    }

    @Test fun `the app filter keeps one package`() {
        val history = history(event(alpha, "2026-09-22T10:00"), event(beta, "2026-09-22T11:00"))
        val filtered = HistoryFilter.apply(history, HistoryTimeRange.WEEK, beta.packageName, now, zone)
        assertEquals(listOf(beta.packageName), filtered.events.map { it.app.packageName })
    }

    @Test fun `the timeline separates records from intervals and leaves out denials`() {
        val timeline = HistoryFilter.timeline(
            listOf(
                event(alpha, "2026-09-22T10:00"),
                event(alpha, "2026-09-10T00:00", interval = "2026-09-05T00:00", accessCount = 6),
                event(beta, "2026-09-22T11:00", interval = "2026-09-22T10:45", accessCount = 0, rejectCount = 3),
            ),
        )
        assertEquals(1, timeline.individual.size)
        assertEquals(listOf(6), timeline.intervals.map { it.event.accessCount })
    }

    @Test fun `individual records and interval counts are never added together`() {
        val events = listOf(
            event(alpha, "2026-09-22T10:00"),
            event(alpha, "2026-09-22T10:05"),
            event(alpha, "2026-09-10T00:00", interval = "2026-09-05T00:00", accessCount = 60),
        )
        val withRecords = history(*events.toTypedArray()).copy(individualRecordsAvailable = true)
        assertEquals(2, withRecords.recordCount)
        assertEquals(60, withRecords.intervalAccessCount)
        val summary = HistoryAppStatistics.summarize(events, individualRecordsAvailable = true).single()
        assertEquals(2, summary.accessCount)
        assertEquals(60, summary.intervalAccessCount)

        val intervalsOnly = history(events.last())
        assertEquals(60, intervalsOnly.recordCount)
        assertEquals(0, intervalsOnly.intervalAccessCount)
    }

    private fun history(vararg events: ResolvedHistoryEvent) =
        PermissionHistory(permission = HistoryPermission("CAMERA"), events = events.toList())

    private fun event(
        app: InstalledApp,
        time: String,
        interval: String? = null,
        accessCount: Int = 1,
        rejectCount: Int = 0,
    ) = ResolvedHistoryEvent(
        event = AppOpHistoryEvent(
            uid = app.uid,
            packageName = app.packageName,
            operationName = "CAMERA",
            attributionTag = null,
            accessTimeMillis = millis(time),
            durationMillis = null,
            uidState = "top",
            flags = "s",
            accessCount = accessCount,
            isAggregated = interval != null,
            rejectCount = rejectCount,
            intervalStartTimeMillis = interval?.let(::millis),
        ),
        app = app,
    )

    private fun millis(time: String): Long =
        LocalDateTime.parse(time).atZone(zone).toInstant().toEpochMilli()
}
