package dev.izumi.appopsnext.presentation.history

import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.history.ArchivedHistory
import dev.izumi.appopsnext.history.model.AppOpHistoryEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryArchiveMergerTest {
    private val app = InstalledApp("Camera", "com.example.camera", 10_166, isSystemApp = false)

    @Test fun `saved records join the system's without repeating the ones both hold`() {
        val merged = HistoryArchiveMerger.merge(
            system = listOf(single(900), single(800)),
            archived = ArchivedHistory(listOf(single(800), single(100)), coverage = listOf(0L..1_000L)),
        )
        assertEquals(listOf(900L, 800L, 100L), merged.map { it.event.accessTimeMillis })
    }

    @Test fun `accesses sharing a minute are matched copy for copy`() {
        val archived = ArchivedHistory(List(3) { single(500) }, coverage = listOf(0L..1_000L))
        assertEquals(3, HistoryArchiveMerger.merge(List(3) { single(500) }, archived).size)
        assertEquals(3, HistoryArchiveMerger.merge(List(1) { single(500) }, archived).size)
    }

    @Test fun `a covered interval keeps only its rejections while a gap keeps its accesses`() {
        val merged = HistoryArchiveMerger.merge(
            system = listOf(
                interval(start = 200, end = 300, accesses = 5, rejections = 2),
                interval(start = 400, end = 500, accesses = 3, rejections = 0),
                interval(start = 2_000, end = 3_000, accesses = 7, rejections = 0),
            ),
            archived = ArchivedHistory(emptyList(), coverage = listOf(0L..1_000L)),
        )
        assertEquals(listOf(3_000L, 300L), merged.map { it.event.accessTimeMillis })
        assertEquals(listOf(7, 0), merged.map { it.event.accessCount })
        assertEquals(2, merged.last().event.rejectCount)
    }

    @Test fun `only saved copies inside the system's span are matched against it`() {
        val archived = ArchivedHistory(
            listOf(single(100), single(100), single(500), single(500), single(2_000)),
            coverage = listOf(0L..3_000L),
        )
        val merged = HistoryArchiveMerger.merge(system = listOf(single(500), single(900)), archived = archived)
        assertEquals(listOf(2_000L, 900L, 500L, 500L, 100L, 100L), merged.map { it.event.accessTimeMillis })
    }

    @Test fun `an interval reaching before the saved period keeps the accesses the records do not hold`() {
        val merged = HistoryArchiveMerger.merge(
            system = listOf(interval(start = 0, end = 2_000, accesses = 10, rejections = 0)),
            archived = ArchivedHistory(
                listOf(single(1_500), single(1_800), single(1_600, uidState = "bg")),
                coverage = listOf(1_000L..5_000L),
            ),
        )
        // Only the two records the interval counts are taken out, not the background one.
        assertEquals(8, merged.single { it.event.isAggregated }.event.accessCount)
        assertEquals(3, merged.count { !it.event.isAggregated })
    }

    @Test fun `intervals around a deleted range keep the accesses beyond the remaining records`() {
        // Deleting 1_000 until 2_000 split the coverage around it.
        val archived = ArchivedHistory(
            listOf(single(600), single(2_200)),
            coverage = listOf(0L..999L, 2_000L..3_000L),
        )
        val merged = HistoryArchiveMerger.merge(
            system = listOf(interval(start = 500, end = 2_500, accesses = 6, rejections = 0)),
            archived = archived,
        )
        assertEquals(4, merged.single { it.event.isAggregated }.event.accessCount)
    }

    @Test fun `a partly covered interval the records account for keeps only its rejections`() {
        val archived = ArchivedHistory(listOf(single(1_500), single(1_600)), coverage = listOf(1_000L..5_000L))
        val withRejections = HistoryArchiveMerger.merge(
            listOf(interval(start = 0, end = 2_000, accesses = 2, rejections = 3)), archived,
        ).single { it.event.isAggregated }.event
        assertEquals(0, withRejections.accessCount)
        assertEquals(3, withRejections.rejectCount)
        val withoutRejections = HistoryArchiveMerger.merge(
            listOf(interval(start = 0, end = 2_000, accesses = 1, rejections = 0)), archived,
        )
        assertTrue(withoutRejections.none { it.event.isAggregated })
    }

    @Test fun `the system's own records are not taken out of its intervals a second time`() {
        val merged = HistoryArchiveMerger.merge(
            system = listOf(single(1_500), interval(start = 0, end = 2_000, accesses = 10, rejections = 0)),
            archived = ArchivedHistory(listOf(single(1_500), single(1_800)), coverage = listOf(1_000L..5_000L)),
        )
        assertEquals(9, merged.single { it.event.isAggregated }.event.accessCount)
    }

    @Test fun `without saved records the system history is unchanged`() {
        val system = listOf(single(900), interval(0, 100, accesses = 1, rejections = 0))
        assertEquals(system, HistoryArchiveMerger.merge(system, null))
    }

    private fun single(time: Long, uidState: String = "top") =
        resolved(time, start = null, accesses = 1, rejections = 0, uidState = uidState)

    private fun interval(start: Long, end: Long, accesses: Int, rejections: Int) =
        resolved(end, start, accesses, rejections)

    private fun resolved(
        time: Long,
        start: Long?,
        accesses: Int,
        rejections: Int,
        uidState: String = "top",
    ) = ResolvedHistoryEvent(
        event = AppOpHistoryEvent(
            uid = app.uid,
            packageName = app.packageName,
            operationName = "CAMERA",
            attributionTag = null,
            accessTimeMillis = time,
            durationMillis = null,
            uidState = uidState,
            flags = "s",
            accessCount = accesses,
            isAggregated = start != null,
            rejectCount = rejections,
            intervalStartTimeMillis = start,
        ),
        app = app,
    )
}
