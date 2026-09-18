package dev.izumi.appopsnext.diagnostics

import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class DiagnosticLogRepositoryTest {
    @Test fun `compaction preserves order without duplicating queued events`() = runBlocking {
        val directory = Files.createTempDirectory("diagnostic-log-test").toFile()
        val file = directory.resolve("events.log")
        val owner = Job()
        try {
            val repository = DiagnosticLogRepository(
                file, persistenceScope = CoroutineScope(coroutineContext + owner),
            )
            // Queue the entire burst before the persistence coroutine can start.
            repeat(1_500) { repository.info("Test", "event-$it") }
            yield()
            val stored = file.readLines()
            assertEquals(stored.size, stored.distinct().size)
            assertEquals((901 until 1_500).toList(), stored.map { it.substringAfter("event-").toInt() })
            assertEquals(repository.lines.value, stored.takeLast(300))
        } finally {
            owner.cancelAndJoin()
            directory.deleteRecursively()
        }
    }

    @Test fun `clear before restore discards old file and respects queued appends`() = runBlocking {
        val directory = Files.createTempDirectory("diagnostic-clear-test").toFile()
        val file = directory.resolve("events.log")
        file.writeText("old-entry\n")
        val owner = Job()
        try {
            val repository = DiagnosticLogRepository(
                file, persistenceScope = CoroutineScope(coroutineContext + owner),
            )
            repeat(1_250) { repository.info("Test", "before-clear-$it") }
            repository.clear()
            repository.info("Test", "after-clear")
            yield()
            assertEquals(1, repository.lines.value.size)
            assertTrue(repository.lines.value.single().endsWith("after-clear"))
            assertEquals(repository.lines.value, file.readLines())
        } finally {
            owner.cancelAndJoin()
            directory.deleteRecursively()
        }
    }

    @Test fun `startup merges stored and queued lines once in order`() = runBlocking {
        val directory = Files.createTempDirectory("diagnostic-restore-test").toFile()
        val file = directory.resolve("events.log")
        file.writeText("old-entry\n")
        val owner = Job()
        try {
            val repository = DiagnosticLogRepository(
                file, persistenceScope = CoroutineScope(coroutineContext + owner),
            )
            repository.info("Test", "new-entry")
            yield()
            assertEquals(2, repository.lines.value.size)
            assertEquals("old-entry", repository.lines.value.first())
            assertEquals(repository.lines.value, file.readLines())
        } finally {
            owner.cancelAndJoin()
            directory.deleteRecursively()
        }
    }
}
