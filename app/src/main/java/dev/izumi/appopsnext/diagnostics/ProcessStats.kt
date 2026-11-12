package dev.izumi.appopsnext.diagnostics

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** The fields of `/proc/<pid>/stat` that the diagnostics use. */
data class ProcessStat(
    val pid: Int,
    /** The kernel's short name, at most 15 characters. */
    val comm: String,
    val parentPid: Int,
    /** Clock ticks from boot to the start of the process. */
    val startTicks: Long,
)

/** One process as the diagnostics list it. */
data class ProcessEntry(
    val pid: Int,
    val parentPid: Int,
    val uid: Int,
    val startEpochMillis: Long,
    val name: String,
)

/**
 * Reads process start times from procfs. The start time in `stat` counts clock
 * ticks from boot, including time spent asleep, so it is turned into a date
 * with the device's tick rate and the wall time of boot.
 */
object ProcessStats {
    /**
     * Parses one `stat` line. The name sits in parentheses and may itself hold
     * spaces and parentheses, so it ends at the last closing parenthesis.
     */
    fun parse(stat: String): ProcessStat? {
        val open = stat.indexOf('(')
        val close = stat.lastIndexOf(')')
        if (open <= 0 || close < open) return null
        val pid = stat.substring(0, open).trim().toIntOrNull() ?: return null
        // After the name: state, ppid, then the fields up to starttime, the 22nd.
        val rest = stat.substring(close + 1).trim().split(' ').filter(String::isNotEmpty)
        if (rest.size < STARTTIME_AFTER_NAME + 1) return null
        val parentPid = rest[PPID_AFTER_NAME].toIntOrNull() ?: return null
        val startTicks = rest[STARTTIME_AFTER_NAME].toLongOrNull() ?: return null
        return ProcessStat(pid, stat.substring(open + 1, close), parentPid, startTicks)
    }

    /**
     * When a process started. [bootEpochMillis] is the wall time at boot, which
     * the current time minus the time since boot, sleep included, gives.
     */
    fun startEpochMillis(startTicks: Long, clockTicksPerSecond: Long, bootEpochMillis: Long): Long {
        require(clockTicksPerSecond > 0) { "clock ticks per second must be positive" }
        return bootEpochMillis + startTicks * 1_000L / clockTicksPerSecond
    }

    /** Date, time and offset to the second, so lines from different days stay apart. */
    fun formatTime(epochMillis: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(epochMillis)
            .truncatedTo(ChronoUnit.SECONDS)
            .atZone(zone)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    /**
     * Whether a process belongs in the diagnostics: this app's own processes,
     * its native daemon, and the Shizuku server. Nothing else on the device is
     * listed.
     */
    fun isRelevant(name: String, packageName: String): Boolean =
        name == packageName ||
            name.startsWith("$packageName:") ||
            name == SHIZUKU_SERVER ||
            name.substringAfterLast('/') == NATIVE_DAEMON

    /** The first argument of a `cmdline` file, which the platform pads with NUL bytes. */
    fun commandName(cmdline: ByteArray): String {
        val end = cmdline.indexOf(0).let { if (it < 0) cmdline.size else it }
        return String(cmdline, 0, end, Charsets.UTF_8)
    }

    /**
     * The relevant processes under [procDir] as raw lines, one per process:
     * UID, name and the `stat` line, separated by tabs. The privileged side
     * sends these, and the app turns them into entries with [decodeRaw].
     */
    fun collectRaw(procDir: File, packageName: String, uidOf: (File) -> Int): String =
        procDir.listFiles().orEmpty()
            .filter { it.name.toIntOrNull() != null }
            .mapNotNull { entry ->
                // A process can exit between the listing and the reads.
                runCatching {
                    val name = commandName(File(entry, "cmdline").readBytes())
                    if (name.isEmpty() || !isRelevant(name, packageName)) return@runCatching null
                    val stat = File(entry, "stat").readText().trim()
                    listOf(uidOf(entry).toString(), clean(name), clean(stat)).joinToString("\t")
                }.getOrNull()
            }
            .sorted()
            .joinToString("\n")

    /**
     * Turns the raw lines back into entries, the oldest first. A line that does
     * not parse, or names a process that is not relevant, is left out, so a
     * privileged side that sends more than it should still lists nothing else.
     */
    fun decodeRaw(
        raw: String,
        packageName: String,
        clockTicksPerSecond: Long,
        bootEpochMillis: Long,
    ): List<ProcessEntry> = raw.lineSequence()
        .mapNotNull { line ->
            val fields = line.split('\t', limit = 3)
            if (fields.size != 3) return@mapNotNull null
            val uid = fields[0].toIntOrNull() ?: return@mapNotNull null
            val name = fields[1]
            if (!isRelevant(name, packageName)) return@mapNotNull null
            val stat = parse(fields[2]) ?: return@mapNotNull null
            ProcessEntry(
                pid = stat.pid,
                parentPid = stat.parentPid,
                uid = uid,
                startEpochMillis = startEpochMillis(stat.startTicks, clockTicksPerSecond, bootEpochMillis),
                name = name,
            )
        }
        .sortedWith(compareBy(ProcessEntry::startEpochMillis, ProcessEntry::pid))
        .toList()

    /** One line of the report, with fixed keys so it can be searched. */
    fun formatEntry(entry: ProcessEntry, zone: ZoneId): String =
        "pid=${entry.pid} ppid=${entry.parentPid} uid=${entry.uid} " +
            "started=${formatTime(entry.startEpochMillis, zone)} name=${entry.name}"

    /**
     * The report's process section: when it was read, one line per process,
     * and how many install watchers there are, which is the usual question.
     */
    fun formatSnapshot(
        entries: List<ProcessEntry>,
        capturedAtMillis: Long,
        clockTicksPerSecond: Long,
        packageName: String,
        zone: ZoneId,
    ): String = buildList {
        add("capturedAt=${formatTime(capturedAtMillis, zone)}, clockTicksPerSecond=$clockTicksPerSecond")
        if (entries.isEmpty()) add("No matching processes.")
        entries.forEach { add(formatEntry(it, zone)) }
        add("installWatchers=${entries.count { it.name == "$packageName:$WATCHER_SUFFIX" }}")
    }.joinToString("\n")

    private fun clean(value: String): String = value.replace('\t', ' ').replace('\n', ' ')

    const val SHIZUKU_SERVER = "shizuku_server"
    /** The suffix of the install watcher's process, as NewAppWatcherService names it. */
    const val WATCHER_SUFFIX = "watcher"
    const val NATIVE_DAEMON = "appopsnextd"
    private const val PPID_AFTER_NAME = 1
    private const val STARTTIME_AFTER_NAME = 19
}
