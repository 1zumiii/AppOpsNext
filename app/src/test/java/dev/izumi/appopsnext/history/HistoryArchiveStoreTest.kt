package dev.izumi.appopsnext.history

import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.history.model.AppOpHistoryEvent
import dev.izumi.appopsnext.presentation.history.ResolvedHistoryEvent
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryArchiveStoreTest {
    private val app = InstalledApp("Camera", "com.example.camera", 10_166, isSystemApp = false)

    @Test fun `a repeated read adds nothing and coverage joins overlapping periods`() = runBlocking {
        withArchive { store, _ ->
            store.record("CAMERA", listOf(event(100), event(200)), coveredFrom = 0, coveredTo = 1_000)
            store.record("CAMERA", listOf(event(200), event(900)), coveredFrom = 500, coveredTo = 1_500)
            val archived = store.contents.value!!.getValue("CAMERA")
            assertEquals(listOf(100L, 200L, 900L), archived.events.map { it.event.accessTimeMillis })
            assertEquals(listOf(0L..1_500L), archived.coverage)
        }
    }

    @Test fun `accesses sharing a minute are kept copy for copy`() = runBlocking {
        withArchive { store, _ ->
            store.record("CAMERA", List(3) { event(100) }, 0, 1_000)
            store.record("CAMERA", List(3) { event(100) }, 0, 1_000)
            store.record("CAMERA", List(2) { event(100) }, 0, 1_000)
            assertEquals(3, store.contents.value!!.getValue("CAMERA").events.size)
        }
    }

    @Test fun `interval counts are never saved`() = runBlocking {
        withArchive { store, _ ->
            store.record("CAMERA", listOf(event(100), event(300, aggregated = true)), 0, 1_000)
            assertEquals(1, store.contents.value!!.getValue("CAMERA").events.size)
        }
    }

    @Test fun `saved records survive a new store and deleting a range removes its records and coverage`() = runBlocking {
        withArchive { store, file ->
            store.record("CAMERA", listOf(event(100), event(2_000)), coveredFrom = 0, coveredTo = 3_000)
            store.flush()
            val reopened = HistoryArchiveStore(file)
            reopened.load()
            assertEquals(2, reopened.contents.value!!.getValue("CAMERA").events.size)

            assertEquals(1, reopened.countBetween(0, 1_000))
            reopened.deleteBetween(0, 1_000)
            val trimmed = reopened.contents.value!!.getValue("CAMERA")
            assertEquals(listOf(2_000L), trimmed.events.map { it.event.accessTimeMillis })
            assertEquals(listOf(1_000L..3_000L), trimmed.coverage)

            // A range in the middle splits the coverage around it.
            reopened.deleteBetween(1_500, 2_500)
            val split = reopened.contents.value!!.getValue("CAMERA")
            assertTrue(split.events.isEmpty())
            assertEquals(listOf(1_000L..1_499L, 2_500L..3_000L), split.coverage)

            reopened.deleteAll()
            assertTrue(reopened.contents.value!!.isEmpty())
            assertFalse(file.exists())
        }
    }

    @Test fun `a full archive drops its oldest records first`() = runBlocking {
        withArchive(maxEvents = 2) { store, _ ->
            store.record("CAMERA", listOf(event(100), event(200), event(300)), 0, 1_000)
            val archived = store.contents.value!!.getValue("CAMERA")
            assertEquals(listOf(200L, 300L), archived.events.map { it.event.accessTimeMillis })
            assertEquals(listOf(101L..1_000L), archived.coverage)
        }
    }

    @Test fun `a full archive with a shared boundary timestamp still reopens`() = runBlocking {
        withArchive(maxEvents = 2) { store, file ->
            store.record("CAMERA", List(3) { event(100) }, 0, 1_000)
            assertEquals(2, store.contents.value!!.getValue("CAMERA").events.size)
            store.flush()
            val reopened = HistoryArchiveStore(file, maxEvents = 2)
            reopened.load()
            assertEquals(2, reopened.contents.value!!.getValue("CAMERA").events.size)
        }
    }

    @Test fun `an unreadable file is kept as it is and nothing is saved over it`() = runBlocking {
        withArchive { store, file ->
            store.record("CAMERA", listOf(event(100)), 0, 1_000)
            store.flush()
            val damaged = file.readBytes().copyOf(file.length().toInt() - 3)
            file.writeBytes(damaged)

            val reopened = HistoryArchiveStore(file)
            reopened.load()
            assertEquals(HistoryArchiveProblem.UNREADABLE, reopened.problem.value)
            assertTrue(reopened.contents.value!!.isEmpty())
            reopened.record("CAMERA", listOf(event(200)), 0, 1_000)
            reopened.deleteBetween(0, 1_000)
            reopened.flush()
            assertTrue(reopened.contents.value!!.isEmpty())
            assertArrayEquals(damaged, file.readBytes())

            // Deleting everything is the way out, and saving resumes after it.
            reopened.deleteAll()
            assertFalse(file.exists())
            assertNull(reopened.problem.value)
            reopened.record("CAMERA", listOf(event(300)), 0, 1_000)
            reopened.flush()
            assertTrue(file.exists())
        }
    }

    @Test fun `a file from a newer version is left for that version`() = runBlocking {
        withArchive { _, file ->
            file.outputStream().use { DataOutputStream(it).apply { writeInt(MAGIC); writeInt(3); writeInt(0) } }
            val written = file.readBytes()
            val reopened = HistoryArchiveStore(file)
            reopened.record("CAMERA", listOf(event(100)), 0, 1_000)
            reopened.flush()
            assertEquals(HistoryArchiveProblem.UNREADABLE, reopened.problem.value)
            assertArrayEquals(written, file.readBytes())
        }
    }

    @Test fun `a file written before the layout was recorded still reads`() = runBlocking {
        withArchive { _, file ->
            file.outputStream().use { stream ->
                DataOutputStream(stream).apply {
                    writeInt(MAGIC)
                    writeInt(1)
                    writeInt(1)
                    writeBoundedString("CAMERA")
                    writeInt(1)
                    writeLong(0)
                    writeLong(1_000)
                    writeInt(1)
                    writeHistoryEvent(event(100))
                    flush()
                }
            }
            val reopened = HistoryArchiveStore(file)
            reopened.load()
            assertNull(reopened.problem.value)
            val archived = reopened.contents.value!!.getValue("CAMERA")
            assertEquals(listOf(100L), archived.events.map { it.event.accessTimeMillis })
            assertEquals(listOf(0L..1_000L), archived.coverage)
        }
    }

    @Test fun `a write past the file limit drops the oldest records until it fits`() = runBlocking {
        withArchive(maxFileBytes = 700) { store, file ->
            store.record("CAMERA", List(10) { event(100L * (it + 1)) }, 0, 2_000)
            store.flush()
            assertNull(store.problem.value)
            val kept = store.contents.value!!.getValue("CAMERA").events.map { it.event.accessTimeMillis }
            assertTrue(kept.size in 1 until 10)
            assertEquals((11 - kept.size..10).map { it * 100L }, kept)

            val reopened = HistoryArchiveStore(file, maxFileBytes = 700)
            reopened.load()
            assertEquals(kept, reopened.contents.value!!.getValue("CAMERA").events.map { it.event.accessTimeMillis })
        }
    }

    @Test fun `a failed write is reported and the next flush retries it`() = runBlocking {
        val directory = Files.createTempDirectory("history-archive").toFile()
        try {
            // A regular file where the archive's directory should be makes every write fail.
            val blocker = File(directory, "blocked").apply { writeText("") }
            val file = File(blocker, "archive.bin")
            val store = HistoryArchiveStore(file)
            store.load()
            store.record("CAMERA", listOf(event(100)), 0, 1_000)
            store.flush()
            assertEquals(HistoryArchiveProblem.WRITE_FAILED, store.problem.value)
            assertEquals(1, store.contents.value!!.getValue("CAMERA").events.size)

            blocker.delete()
            store.flush()
            assertNull(store.problem.value)
            assertTrue(file.isFile)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun event(time: Long, aggregated: Boolean = false) = ResolvedHistoryEvent(
        event = AppOpHistoryEvent(
            uid = app.uid,
            packageName = app.packageName,
            operationName = "CAMERA",
            attributionTag = null,
            accessTimeMillis = time,
            durationMillis = null,
            uidState = "top",
            flags = "s",
            isAggregated = aggregated,
        ),
        app = app,
    )

    private suspend fun withArchive(
        maxEvents: Int = HistoryArchiveStore.MAX_EVENTS,
        maxFileBytes: Long = 32L * 1024 * 1024,
        block: suspend (HistoryArchiveStore, File) -> Unit,
    ) {
        val directory = Files.createTempDirectory("history-archive").toFile()
        try {
            val file = File(directory, "archive.bin")
            val store = HistoryArchiveStore(file, maxEvents, maxFileBytes)
            store.load()
            block(store, file)
        } finally {
            directory.deleteRecursively()
        }
    }

    private companion object {
        const val MAGIC = 0x48415243
    }
}
