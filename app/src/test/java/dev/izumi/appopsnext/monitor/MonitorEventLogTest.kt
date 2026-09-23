package dev.izumi.appopsnext.monitor

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorEventLogTest {
    @Test fun `entries written in batches read back in order after reopening`() = withLog { file, open ->
        val log = open()
        log.record(entry(100), elapsedRealtimeMillis = 1_000)
        log.record(entry(200, allowed = false), elapsedRealtimeMillis = 2_000)
        log.flush()
        log.record(entry(300), elapsedRealtimeMillis = 3_000)
        log.flush()

        val reopened = open()
        reopened.load()
        assertEquals(listOf(100L, 200L, 300L), reopened.entries.value!!.map { it.timeMillis })
        assertEquals(listOf(true, false, true), reopened.entries.value!!.map { it.allowed })
        assertTrue(file.isFile)
    }

    @Test fun `one access reported twice is written once, but an allowed and a refused one both are`() = withLog { _, open ->
        val log = open()
        log.record(entry(100), elapsedRealtimeMillis = 1_000)
        log.record(entry(101), elapsedRealtimeMillis = 1_008)
        log.record(entry(102, allowed = false), elapsedRealtimeMillis = 1_010)
        log.record(entry(400), elapsedRealtimeMillis = 1_300)
        log.flush()
        assertEquals(listOf(100L, 102L, 400L), log.entries.value!!.map { it.timeMillis })
    }

    @Test fun `a full log drops its oldest entries and still reopens`() = withLog(maxEntries = 3) { _, open ->
        val log = open()
        repeat(10) { log.record(entry(it * 1_000L), elapsedRealtimeMillis = it * 1_000L) }
        log.flush()
        assertEquals(listOf(7_000L, 8_000L, 9_000L), log.entries.value!!.map { it.timeMillis })
        val reopened = open()
        reopened.load()
        assertEquals(listOf(7_000L, 8_000L, 9_000L), reopened.entries.value!!.map { it.timeMillis })
    }

    @Test fun `an entry cut short is dropped and later ones still append after it`() = withLog { file, open ->
        val log = open()
        log.record(entry(100), elapsedRealtimeMillis = 1_000)
        log.record(entry(200), elapsedRealtimeMillis = 2_000)
        log.flush()
        // The process ended three bytes into the second entry's time.
        val bytes = file.readBytes()
        val entrySize = (bytes.size - HEADER_BYTES) / 2
        file.writeBytes(bytes.copyOf(HEADER_BYTES + entrySize + 3))

        val reopened = open()
        reopened.load()
        assertEquals(listOf(100L), reopened.entries.value!!.map { it.timeMillis })
        reopened.record(entry(300), elapsedRealtimeMillis = 3_000)
        reopened.flush()
        val again = open()
        again.load()
        assertEquals(listOf(100L, 300L), again.entries.value!!.map { it.timeMillis })
    }

    @Test fun `clearing removes the file and the entries`() = withLog { file, open ->
        val log = open()
        log.record(entry(100), elapsedRealtimeMillis = 1_000)
        log.flush()
        log.clear()
        assertTrue(log.entries.value!!.isEmpty())
        assertFalse(file.exists())
    }

    private fun entry(time: Long, allowed: Boolean = true) =
        MonitorLogEntry(time, 10_166, "com.example.camera", "CAMERA", allowed)

    private fun withLog(
        maxEntries: Int = MonitorEventLog.MAX_ENTRIES,
        block: suspend (File, () -> MonitorEventLog) -> Unit,
    ) = runBlocking {
        val directory = Files.createTempDirectory("monitor-log").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val file = File(directory, "log.bin")
            // A long batch delay leaves every write to the explicit flushes.
            block(file) { MonitorEventLog(file, scope, maxEntries, batchDelayMillis = 60_000) }
        } finally {
            scope.cancel()
            directory.deleteRecursively()
        }
    }

    private companion object {
        const val HEADER_BYTES = 8
    }
}
