package dev.izumi.appopsnext.presentation.history

import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.history.ArchivedHistory
import dev.izumi.appopsnext.history.model.AppOpHistoryEvent
import org.junit.Assert.assertEquals
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

    @Test fun `without saved records the system history is unchanged`() {
        val system = listOf(single(900), interval(0, 100, accesses = 1, rejections = 0))
        assertEquals(system, HistoryArchiveMerger.merge(system, null))
    }

    private fun single(time: Long) = resolved(time, start = null, accesses = 1, rejections = 0)

    private fun interval(start: Long, end: Long, accesses: Int, rejections: Int) =
        resolved(end, start, accesses, rejections)

    private fun resolved(time: Long, start: Long?, accesses: Int, rejections: Int) = ResolvedHistoryEvent(
        event = AppOpHistoryEvent(
            uid = app.uid,
            packageName = app.packageName,
            operationName = "CAMERA",
            attributionTag = null,
            accessTimeMillis = time,
            durationMillis = null,
            uidState = "top",
            flags = "s",
            accessCount = accesses,
            isAggregated = start != null,
            rejectCount = rejections,
            intervalStartTimeMillis = start,
        ),
        app = app,
    )
}
