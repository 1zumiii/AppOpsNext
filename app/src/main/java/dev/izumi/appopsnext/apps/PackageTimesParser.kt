package dev.izumi.appopsnext.apps

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Install and update times of one package for one user, in epoch milliseconds. */
data class PackageTimes(
    val firstInstallTimeMillis: Long?,
    val lastUpdateTimeMillis: Long?,
)

/**
 * Reads the install and update times of other users from `dumpsys package
 * packages`, where each package block carries one update time and a
 * `User N:` line per user followed by that user's first install time:
 *
 * ```
 *   Package [com.example] (33a354):
 *     lastUpdateTime=2026-10-29 22:10:29
 *     User 10: ceDataInode=... installed=true ...
 *       firstInstallTime=2026-10-15 20:29:41
 * ```
 *
 * Times are printed in the device's time zone, which is this process's.
 */
object PackageTimesParser {
    private val packageLine = Regex("""^\s*Package \[([^\]]+)\]""")
    private val userLine = Regex("""^\s*User (\d+):""")
    private const val LAST_UPDATE = "lastUpdateTime="
    private const val FIRST_INSTALL = "firstInstallTime="
    private val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun parse(dump: String, userId: Int, zone: ZoneId = ZoneId.systemDefault()): Map<String, PackageTimes> {
        val times = mutableMapOf<String, PackageTimes>()
        var current: String? = null
        var inUser = false
        dump.lineSequence().forEach { line ->
            packageLine.find(line)?.let { match ->
                val name = match.groupValues[1]
                // Later sections, such as hidden system packages, list a package again.
                current = name.takeIf { it !in times }
                inUser = false
                current?.let { times[it] = PackageTimes(null, null) }
                return@forEach
            }
            val name = current ?: return@forEach
            val trimmed = line.trim()
            val user = userLine.find(line)
            when {
                trimmed.startsWith(LAST_UPDATE) -> {
                    times[name] = times.getValue(name)
                        .copy(lastUpdateTimeMillis = millis(trimmed.removePrefix(LAST_UPDATE), zone))
                }
                user != null -> inUser = user.groupValues[1].toIntOrNull() == userId
                inUser && trimmed.startsWith(FIRST_INSTALL) -> {
                    times[name] = times.getValue(name)
                        .copy(firstInstallTimeMillis = millis(trimmed.removePrefix(FIRST_INSTALL), zone))
                    inUser = false
                }
            }
        }
        return times
    }

    /** Only the lines [parse] reads, so the result fits in a binder transaction. */
    fun compact(dump: String): String =
        dump.lineSequence()
            .filter { line ->
                val trimmed = line.trim()
                packageLine.containsMatchIn(line) || trimmed.startsWith(LAST_UPDATE) ||
                    trimmed.startsWith(FIRST_INSTALL) || userLine.containsMatchIn(line)
            }
            .map { line -> userLine.find(line)?.let { "    User ${it.groupValues[1]}:" } ?: line }
            .joinToString("\n")

    private fun millis(text: String, zone: ZoneId): Long? = runCatching {
        LocalDateTime.parse(text.trim(), format).atZone(zone).toInstant().toEpochMilli()
    }.getOrNull()?.takeIf { it > 0 }
}
