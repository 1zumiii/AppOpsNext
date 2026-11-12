package dev.izumi.appopsnext.diagnostics

import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticLogTiersTest {
    private class TestClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
        fun advance(duration: Duration) {
            now += duration
        }
    }

    private val start = Instant.parse("2026-11-12T08:00:00Z")

    private fun withRepository(
        prepare: (File) -> Unit = {},
        test: suspend (DiagnosticLogRepository, TestClock, File) -> Unit,
    ) = runBlocking {
        val directory = Files.createTempDirectory("diagnostic-tiers-test").toFile()
        val file = directory.resolve("events.log")
        prepare(directory)
        val owner = Job()
        try {
            val clock = TestClock(start)
            val repository = DiagnosticLogRepository(
                file, clock, persistenceScope = CoroutineScope(coroutineContext + owner),
            )
            test(repository, clock, directory)
        } finally {
            owner.cancelAndJoin()
            directory.deleteRecursively()
        }
    }

    @Test fun `identical lines within the window fold into the first with a count`() =
        withRepository { repository, clock, directory ->
            repository.info("Test", "same")
            clock.advance(Duration.ofSeconds(30))
            repository.info("Test", "same")
            clock.advance(Duration.ofSeconds(30))
            repository.info("Test", "same")
            yield()
            val expected = "2026-11-12T08:00:00Z [INFO] [Test] same (x3, last 08:01:00)"
            assertEquals(listOf(expected), repository.lines.value)
            assertEquals(listOf(expected), directory.resolve("events.log").readLines())
        }

    @Test fun `a repeat after the window starts a new line`() =
        withRepository { repository, clock, _ ->
            repository.info("Test", "same")
            clock.advance(Duration.ofMinutes(4))
            repository.info("Test", "same")
            clock.advance(Duration.ofSeconds(61))
            repository.info("Test", "same")
            assertEquals(
                listOf(
                    "2026-11-12T08:00:00Z [INFO] [Test] same (x2, last 08:04:00)",
                    "2026-11-12T08:05:01Z [INFO] [Test] same",
                ),
                repository.lines.value,
            )
        }

    @Test fun `a different line in between, another level or source ends the fold`() =
        withRepository { repository, _, _ ->
            repository.info("Test", "same")
            repository.info("Test", "other")
            repository.info("Test", "same")
            repository.info("Elsewhere", "same")
            assertEquals(4, repository.lines.value.size)
            assertTrue(repository.lines.value.none { "(x" in it })
        }

    @Test fun `monitor, MIUI and write failure lines are never folded`() =
        withRepository { repository, _, _ ->
            repeat(2) { repository.info("Monitor", "Watcher registry=CONFIRMED, expected=[]") }
            repeat(2) { repository.info("Other", "MIUI watch noted [10008, 10021]: accepted") }
            repeat(2) { repository.warning("AppOpsWrite", "Write failed. requested=IGNORE, original=ALLOW") }
            assertEquals(6, repository.lines.value.size)
            assertTrue(repository.lines.value.none { "(x" in it })
        }

    @Test fun `a fold in one tier leaves the other tier's lines in time order`() =
        withRepository { repository, clock, directory ->
            repository.info("Test", "routine")
            clock.advance(Duration.ofSeconds(1))
            repository.warning("Test", "warned")
            clock.advance(Duration.ofSeconds(1))
            repository.info("Test", "routine")
            yield()
            assertEquals(
                listOf(
                    "2026-11-12T08:00:00Z [INFO] [Test] routine (x2, last 08:00:02)",
                    "2026-11-12T08:00:01Z [WARNING] [Test] warned",
                ),
                repository.lines.value,
            )
            assertEquals(
                listOf("2026-11-12T08:00:01Z [WARNING] [Test] warned"),
                directory.resolve("events-important.log").readLines(),
            )
        }

    @Test fun `a full routine tier does not evict important lines`() =
        withRepository { repository, clock, directory ->
            repository.info("Test", "started", DiagnosticLogTier.IMPORTANT)
            repository.warning("Test", "warned")
            repeat(450) {
                clock.advance(Duration.ofSeconds(1))
                repository.info("Test", "routine-$it")
            }
            yield()
            val lines = repository.lines.value
            assertEquals(302, lines.size)
            assertTrue(lines[0].endsWith("started"))
            assertTrue(lines[1].endsWith("warned"))
            assertTrue(lines[2].endsWith("routine-150"))
            assertEquals(2, directory.resolve("events-important.log").readLines().size)
            // The file is compacted later, at four times the quota.
            assertEquals(
                (150 until 450).map { "routine-$it" },
                directory.resolve("events.log").readLines().takeLast(300).map { it.substringAfter("] [Test] ") },
            )
        }

    @Test fun `a full important tier does not evict routine lines`() =
        withRepository { repository, clock, directory ->
            repository.info("Test", "routine")
            repeat(250) {
                clock.advance(Duration.ofSeconds(1))
                repository.error("Test", "failed-$it")
            }
            yield()
            val lines = repository.lines.value
            assertEquals(201, lines.size)
            assertTrue(lines[0].endsWith("routine"))
            assertTrue(lines[1].endsWith("failed-50"))
            assertEquals(250, directory.resolve("events-important.log").readLines().size)
            assertEquals(1, directory.resolve("events.log").readLines().size)
        }

    @Test fun `both tiers are merged by time`() =
        withRepository { repository, clock, _ ->
            repository.info("Test", "one")
            clock.advance(Duration.ofSeconds(1))
            repository.warning("Test", "two")
            clock.advance(Duration.ofSeconds(1))
            repository.info("Test", "three")
            clock.advance(Duration.ofSeconds(1))
            repository.info("Test", "four", DiagnosticLogTier.IMPORTANT)
            clock.advance(Duration.ofSeconds(1))
            repository.info("Test", "five")
            assertEquals(
                listOf("one", "two", "three", "four", "five"),
                repository.lines.value.map { it.substringAfterLast(' ') },
            )
        }

    @Test fun `a log file from before the tiers loads and moves its warnings`() =
        withRepository(
            prepare = { directory ->
                directory.resolve("events.log").writeText(
                    "2026-11-11T08:00:00+08:00 [INFO] [Application] Process started.\n" +
                        "2026-11-11T08:00:01+08:00 [WARNING] [Shizuku] Shizuku binder died.\n" +
                        "2026-11-11T08:00:02+08:00 [INFO] [AppList] Installed applications loaded. count=3\n" +
                        "2026-11-11T08:00:03+08:00 [ERROR] [Guard] Guard check failed.\n",
                )
            },
        ) { repository, _, directory ->
            repository.info("Test", "new")
            yield()
            assertEquals(
                listOf(
                    "2026-11-11T08:00:00+08:00 [INFO] [Application] Process started.",
                    "2026-11-11T08:00:01+08:00 [WARNING] [Shizuku] Shizuku binder died.",
                    "2026-11-11T08:00:02+08:00 [INFO] [AppList] Installed applications loaded. count=3",
                    "2026-11-11T08:00:03+08:00 [ERROR] [Guard] Guard check failed.",
                    "2026-11-12T08:00:00Z [INFO] [Test] new",
                ),
                repository.lines.value,
            )
            assertEquals(
                listOf(
                    "2026-11-11T08:00:01+08:00 [WARNING] [Shizuku] Shizuku binder died.",
                    "2026-11-11T08:00:03+08:00 [ERROR] [Guard] Guard check failed.",
                ),
                directory.resolve("events-important.log").readLines(),
            )
            assertEquals(
                listOf(
                    "2026-11-11T08:00:00+08:00 [INFO] [Application] Process started.",
                    "2026-11-11T08:00:02+08:00 [INFO] [AppList] Installed applications loaded. count=3",
                    "2026-11-12T08:00:00Z [INFO] [Test] new",
                ),
                directory.resolve("events.log").readLines(),
            )
        }

    @Test fun `a fold after a restart rewrites only the restored file's last line`() =
        withRepository(
            prepare = { directory ->
                directory.resolve("events.log").writeText("2026-11-12T07:00:00Z [INFO] [Test] old\n")
            },
        ) { repository, _, directory ->
            yield()
            repository.info("Test", "same")
            repository.info("Test", "same")
            yield()
            assertEquals(
                listOf(
                    "2026-11-12T07:00:00Z [INFO] [Test] old",
                    "2026-11-12T08:00:00Z [INFO] [Test] same (x2, last 08:00:00)",
                ),
                directory.resolve("events.log").readLines(),
            )
        }

    @Test fun `notes survive a restart and are dropped with the log`() = runBlocking {
        val directory = Files.createTempDirectory("diagnostic-marks-test").toFile()
        val file = directory.resolve("events.log")
        val owner = Job()
        try {
            val scope = CoroutineScope(coroutineContext + owner)
            val first = DiagnosticLogRepository(file, persistenceScope = scope)
            assertTrue(first.noteIfChanged("count", "3"))
            assertFalse(first.noteIfChanged("count", "3"))
            assertTrue(first.noteIfChanged("count", "4"))
            yield()

            val second = DiagnosticLogRepository(file, persistenceScope = scope)
            assertFalse(second.noteIfChanged("count", "4"))
            second.clear()
            assertTrue(second.noteIfChanged("count", "4"))
            yield()

            val third = DiagnosticLogRepository(file, persistenceScope = scope)
            assertFalse(third.noteIfChanged("count", "4"))
        } finally {
            owner.cancelAndJoin()
            directory.deleteRecursively()
        }
    }
}
