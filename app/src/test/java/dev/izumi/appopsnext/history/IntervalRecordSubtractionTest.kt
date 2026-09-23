package dev.izumi.appopsnext.history

import dev.izumi.appopsnext.history.model.AppOpHistoryEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class IntervalRecordSubtractionTest {
    @Test fun `records on either bound count and records outside do not`() {
        val records = IntervalRecordSubtraction(listOf(record(99), record(100), record(150), record(200), record(201)))
        assertEquals(7, records.remainder(interval(accesses = 10))!!.accessCount)
    }

    @Test fun `an interval without records inside keeps its duration and counts`() {
        val untouched = interval(accesses = 4)
        assertSame(untouched, IntervalRecordSubtraction(listOf(record(500))).remainder(untouched))
    }

    @Test fun `more records than accesses leaves nothing but rejections`() {
        val records = IntervalRecordSubtraction(listOf(record(120), record(130)))
        assertNull(records.remainder(interval(accesses = 1)))
        assertEquals(0, records.remainder(interval(accesses = 1, rejections = 2))!!.accessCount)
    }

    @Test fun `records of another attribution are not taken out`() {
        val records = IntervalRecordSubtraction(listOf(record(120, tag = "maps")))
        assertEquals(4, records.remainder(interval(accesses = 4))!!.accessCount)
    }

    private fun record(time: Long, tag: String? = null) = event(time, tag)

    private fun interval(accesses: Int, rejections: Int = 0) = event(200, null).copy(
        accessCount = accesses,
        rejectCount = rejections,
        isAggregated = true,
        intervalStartTimeMillis = 100,
        durationMillis = 5_000,
    )

    private fun event(time: Long, tag: String?) = AppOpHistoryEvent(
        uid = 10_166,
        packageName = "com.example.camera",
        operationName = "CAMERA",
        attributionTag = tag,
        accessTimeMillis = time,
        durationMillis = null,
        uidState = "top",
        flags = "s",
    )
}
