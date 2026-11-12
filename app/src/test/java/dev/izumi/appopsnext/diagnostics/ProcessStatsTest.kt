package dev.izumi.appopsnext.diagnostics

import java.io.File
import java.nio.file.Files
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessStatsTest {
    private val zone: ZoneId = ZoneOffset.ofHours(8)
    private val packageName = "dev.izumi.appopsnext"

    /** A stat line with [name] and the start time in field 22. */
    private fun stat(pid: Int, name: String, ppid: Int, startTicks: Long) =
        "$pid ($name) S $ppid $pid $pid 0 -1 4194560 100 0 0 0 1 2 0 0 20 0 1 0 $startTicks 1000 200 18446744073709551615"

    @Test fun `parses the fields around a plain name`() {
        val parsed = ProcessStats.parse(stat(4321, "app_process", 1, 987_654))
        assertEquals(ProcessStat(4321, "app_process", 1, 987_654), parsed)
    }

    @Test fun `a name with spaces and parentheses ends at the last parenthesis`() {
        val parsed = ProcessStats.parse(stat(77, "odd (name) here) x", 12, 5_000))!!
        assertEquals("odd (name) here) x", parsed.comm)
        assertEquals(12, parsed.parentPid)
        assertEquals(5_000L, parsed.startTicks)
    }

    @Test fun `text that is not a stat line gives nothing`() {
        assertNull(ProcessStats.parse(""))
        assertNull(ProcessStats.parse("12 (short) S 1 2"))
        assertNull(ProcessStats.parse("abc (name) S 1 1 1 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 5"))
    }

    @Test fun `start time follows the tick rate of the device`() {
        val boot = 1_700_000_000_000L
        assertEquals(boot + 90_000L, ProcessStats.startEpochMillis(9_000, 100, boot))
        assertEquals(boot + 36_000L, ProcessStats.startEpochMillis(9_000, 250, boot))
        assertEquals(boot + 9_000L, ProcessStats.startEpochMillis(9_000, 1_000, boot))
    }

    @Test fun `times carry the date and the offset`() {
        assertEquals("2023-11-15T06:13:20+08:00", ProcessStats.formatTime(1_700_000_000_123L, zone))
    }

    @Test fun `only the app, its daemon and the Shizuku server are relevant`() {
        assertTrue(ProcessStats.isRelevant(packageName, packageName))
        assertTrue(ProcessStats.isRelevant("$packageName:watcher", packageName))
        assertTrue(ProcessStats.isRelevant("$packageName:appops", packageName))
        assertTrue(ProcessStats.isRelevant("/data/local/tmp/appopsnext/42/appopsnextd", packageName))
        assertTrue(ProcessStats.isRelevant("shizuku_server", packageName))
        assertFalse(ProcessStats.isRelevant("com.example.other", packageName))
        assertFalse(ProcessStats.isRelevant("$packageName.other", packageName))
        assertFalse(ProcessStats.isRelevant("moe.shizuku.privileged.api", packageName))
        assertFalse(ProcessStats.isRelevant("appopsnextd-helper", packageName))
    }

    @Test fun `decoding drops lines that are malformed or about other processes`() {
        val raw = listOf(
            "2000\t$packageName:watcher\t${stat(300, "ppsnext:watcher", 299, 20_000)}",
            "10234\t$packageName\t${stat(200, "zumi.appopsnext", 100, 10_000)}",
            "10500\tcom.example.other\t${stat(400, "example.other", 100, 1)}",
            "not a line",
            "2000\tshizuku_server\tbroken",
        ).joinToString("\n")

        val entries = ProcessStats.decodeRaw(raw, packageName, clockTicksPerSecond = 100, bootEpochMillis = 0)

        assertEquals(listOf(200, 300), entries.map(ProcessEntry::pid))
        assertEquals(ProcessEntry(300, 299, 2000, 200_000, "$packageName:watcher"), entries[1])
    }

    @Test fun `the snapshot lists each process and counts the watchers`() {
        val entries = listOf(
            ProcessEntry(10, 1, 2000, 1_700_000_000_000L, "shizuku_server"),
            ProcessEntry(11, 10, 2000, 1_700_000_100_000L, "$packageName:watcher"),
            ProcessEntry(12, 10, 2000, 1_700_000_200_000L, "$packageName:watcher"),
        )

        val text = ProcessStats.formatSnapshot(entries, 1_700_000_300_000L, 100, packageName, zone)

        assertEquals(
            listOf(
                "capturedAt=2023-11-15T06:18:20+08:00, clockTicksPerSecond=100",
                "pid=10 ppid=1 uid=2000 started=2023-11-15T06:13:20+08:00 name=shizuku_server",
                "pid=11 ppid=10 uid=2000 started=2023-11-15T06:15:00+08:00 name=$packageName:watcher",
                "pid=12 ppid=10 uid=2000 started=2023-11-15T06:16:40+08:00 name=$packageName:watcher",
                "installWatchers=2",
            ),
            text.lines(),
        )
        assertTrue(
            ProcessStats.formatSnapshot(emptyList(), 0, 100, packageName, zone).contains("No matching processes."),
        )
    }

    @Test fun `collecting from procfs reads only the relevant processes`() {
        val proc = Files.createTempDirectory("proc").toFile()
        try {
            fun process(pid: Int, cmdline: String) = File(proc, pid.toString()).apply {
                mkdirs()
                File(this, "cmdline").writeBytes((cmdline + "\u0000").toByteArray())
                File(this, "stat").writeText(stat(pid, "short", 1, 100) + "\n")
            }
            process(1, "$packageName:watcher")
            process(2, "com.example.other")
            process(3, "shizuku_server")
            File(proc, "self").mkdirs()

            val raw = ProcessStats.collectRaw(proc, packageName) { 2000 }

            assertEquals(2, raw.lines().size)
            assertFalse(raw.contains("com.example.other"))
            assertEquals(listOf(1, 3), ProcessStats.decodeRaw(raw, packageName, 100, 0).map(ProcessEntry::pid))
        } finally {
            proc.deleteRecursively()
        }
    }
}
