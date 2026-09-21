package dev.izumi.appopsnext.monitor

import org.junit.Assert.*
import org.junit.Test

class MonitorAccessAccumulatorTest {
    private fun access(
        time: Long,
        uid: Int = 10001,
        wall: Long = time,
        operation: String = "android:camera",
        allowed: Boolean = true,
    ) = MonitoredAccess(
        uid, operation, "example.app", "Example", AppOpAccessKind.STARTED,
        allowed, wall, time,
    )

    /** Without a throttle, every access the monitor is told about is reported. */
    @Test fun `every access is reported when no interval is set`() {
        val accumulator = MonitorAccessAccumulator()
        for (second in 0..9) assertTrue(accumulator.add(access(second * 1_000L), null))
        assertEquals(10, accumulator.accesses.single().count)
    }

    /**
     * One clipboard read is reported twice a few milliseconds apart, so the only
     * suppression nobody configures is that duplicate.
     */
    @Test fun `a duplicate callback is never reported twice`() {
        val accumulator = MonitorAccessAccumulator()
        assertTrue(accumulator.add(access(0, operation = "android:read_clipboard"), null))
        assertFalse(accumulator.add(access(6, operation = "android:read_clipboard"), null))
        assertTrue(accumulator.add(access(500, operation = "android:read_clipboard"), null))
        assertEquals(2, accumulator.accesses.single().count)
    }

    @Test fun `an interval reports at most once inside it`() {
        val accumulator = MonitorAccessAccumulator()
        val location = { time: Long -> access(time, operation = "android:fine_location") }
        assertTrue(accumulator.add(location(0), 60_000L))
        for (second in 1..59) assertFalse(accumulator.add(location(second * 1_000L), 60_000L))
        assertTrue(accumulator.add(location(60_000L), 60_000L))
        assertEquals(2, accumulator.accesses.single().count)
    }

    @Test fun `each point carries its own interval`() {
        val accumulator = MonitorAccessAccumulator()
        accumulator.add(access(0, operation = "android:fine_location"), 60_000L)
        assertTrue(accumulator.add(access(0, operation = "android:read_sms"), null))
        assertFalse(accumulator.add(access(1_000, operation = "android:fine_location"), 60_000L))
        assertTrue(accumulator.add(access(1_000, operation = "android:read_sms"), null))
        assertEquals(2, accumulator.accesses.size)
    }

    @Test fun `same package in different profiles never merges`() {
        val accumulator = MonitorAccessAccumulator()
        accumulator.add(access(100, 10001), null)
        accumulator.add(access(110, 1010001), null)
        assertEquals(2, accumulator.accesses.size)
    }

    @Test fun `a refusal is counted apart from an allowed access`() {
        val accumulator = MonitorAccessAccumulator()
        accumulator.add(access(0), null)
        assertTrue(accumulator.add(access(1_000, allowed = false), null))
        assertEquals(2, accumulator.accesses.size)
    }

    /** A clock that has gone backwards must not hold an access back. */
    @Test fun `a rolled back clock does not suppress a report`() {
        val accumulator = MonitorAccessAccumulator()
        accumulator.add(access(100_000, wall = 100_000), 60_000L)
        assertTrue(accumulator.add(access(1, wall = 1), 60_000L))
        assertEquals(2, accumulator.accesses.single().count)
    }

    @Test fun `clear removes counts and history stays bounded`() {
        val accumulator = MonitorAccessAccumulator()
        repeat(150) { accumulator.add(access(it * 60_000L, uid = 10_000 + it), null) }
        assertEquals(100, accumulator.accesses.size)
        accumulator.clear()
        assertTrue(accumulator.add(access(1), null))
        assertEquals(1, accumulator.accesses.single().count)
    }
}
