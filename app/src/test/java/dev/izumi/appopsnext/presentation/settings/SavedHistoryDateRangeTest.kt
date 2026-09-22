package dev.izumi.appopsnext.presentation.settings

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedHistoryDateRangeTest {
    private val earliest = LocalDate.of(2026, 9, 15)
    private val latest = LocalDate.of(2026, 9, 22)
    private val range = SavedHistoryDateRange(LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 18))

    @Test fun `dates outside the saved span are pulled back into it`() {
        assertEquals(earliest, range.withStart(LocalDate.of(2026, 1, 1), earliest, latest).start)
        assertEquals(latest, range.withEnd(LocalDate.of(2027, 1, 1), earliest, latest).end)
    }

    @Test fun `the start never passes the end`() {
        val movedStart = range.withStart(LocalDate.of(2026, 9, 20), earliest, latest)
        assertEquals(SavedHistoryDateRange(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 20)), movedStart)
        val movedEnd = range.withEnd(LocalDate.of(2026, 9, 15), earliest, latest)
        assertEquals(SavedHistoryDateRange(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 15)), movedEnd)
    }

    @Test fun `a range covers both end days in full`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val (from, to) = range.millis(zone)
        assertEquals(LocalDateTime.of(2026, 9, 16, 0, 0).atZone(zone).toInstant().toEpochMilli(), from)
        assertEquals(LocalDateTime.of(2026, 9, 19, 0, 0).atZone(zone).toInstant().toEpochMilli(), to)
    }
}
