package dev.izumi.appopsnext.newapps

import dev.izumi.appopsnext.diagnostics.ProcessStats
import java.time.ZoneId

/**
 * What the install watcher found when it started. The watcher runs under the
 * shell or root UID and cannot write the app's files, so it hands this back
 * over its binder and the app writes the diagnostic lines.
 */
data class WatcherStartReport(
    val pid: Int,
    val uid: Int,
    /** Null when the watcher could not read its own start time. */
    val startEpochMillis: Long?,
    /** Null when the watcher could not look for stale watchers. */
    val stale: List<StaleWatcher>?,
    val scanError: String? = null,
) {
    data class StaleWatcher(
        val pid: Int,
        val startEpochMillis: Long?,
        /** Null when the process was ended, otherwise why it was not. */
        val killError: String?,
    )

    /** The line about the watcher itself. */
    fun runningLine(connectMillis: Long?, zone: ZoneId): String = buildString {
        append("Install watcher running. pid=$pid, uid=$uid, started=")
        append(startEpochMillis?.let { ProcessStats.formatTime(it, zone) } ?: "unknown")
        if (connectMillis != null) append(", connectMs=$connectMillis")
    }

    /** The line about the stale watchers it looked for, found or not. */
    fun staleLine(zone: ZoneId): String {
        val found = stale ?: return "Install watcher stale check at start failed. error=${scanError ?: "unknown"}"
        if (found.isEmpty()) return "Install watcher stale check at start. found=0"
        val shown = found.take(MAX_STALE_IN_LINE).joinToString(", ") { process ->
            val started = process.startEpochMillis?.let { ProcessStats.formatTime(it, zone) } ?: "unknown"
            val result = process.killError?.let { "failed($it)" } ?: "ended"
            "pid=${process.pid} started=$started result=$result"
        }
        val more = (found.size - MAX_STALE_IN_LINE).takeIf { it > 0 }?.let { ", more=$it" }.orEmpty()
        return "Install watcher stale check at start. found=${found.size}, processes=[$shown]$more"
    }

    companion object {
        private const val MAX_STALE_IN_LINE = 5

        /**
         * The lines for a watcher that just connected. [reportText] is what
         * [INewAppWatcher.startReport] returned, null when the watcher is too
         * old to answer it, in which case only the running line is written.
         */
        fun connectLines(
            pid: Int,
            reportText: String?,
            connectMillis: Long?,
            zone: ZoneId,
            staleAlreadyLogged: (WatcherStartReport) -> Boolean = { false },
        ): List<String> {
            val report = decode(reportText)?.takeIf { it.pid == pid }
                ?: return listOf(
                    "Install watcher running. pid=$pid" +
                        (connectMillis?.let { ", connectMs=$it" }.orEmpty()) +
                        ", startReport=unsupported",
                )
            return listOfNotNull(
                report.runningLine(connectMillis, zone),
                report.staleLine(zone).takeUnless { staleAlreadyLogged(report) },
            )
        }

        /** A plain text form, since the binder interface carries only a string. */
        fun encode(report: WatcherStartReport): String = buildList {
            add(listOf("v1", report.pid, report.uid, report.startEpochMillis ?: "-").joinToString(" "))
            report.stale?.forEach { process ->
                add(
                    listOf(
                        "stale",
                        process.pid,
                        process.startEpochMillis ?: "-",
                        process.killError?.let(::token) ?: "ended",
                    ).joinToString(" "),
                )
            } ?: add("scan-failed ${token(report.scanError ?: "unknown")}")
        }.joinToString("\n")

        /** Null for text that is not a report, as from a watcher that predates it. */
        fun decode(text: String?): WatcherStartReport? {
            val lines = text?.lines()?.filter(String::isNotBlank).orEmpty()
            val head = lines.firstOrNull()?.split(' ') ?: return null
            if (head.size != 4 || head[0] != "v1") return null
            val pid = head[1].toIntOrNull() ?: return null
            val uid = head[2].toIntOrNull() ?: return null
            var scanError: String? = null
            val stale = mutableListOf<StaleWatcher>()
            for (line in lines.drop(1)) {
                val fields = line.split(' ')
                when {
                    fields.size == 4 && fields[0] == "stale" -> stale += StaleWatcher(
                        pid = fields[1].toIntOrNull() ?: return null,
                        startEpochMillis = fields[2].toLongOrNull(),
                        killError = fields[3].takeUnless { it == "ended" },
                    )
                    fields.size == 2 && fields[0] == "scan-failed" -> scanError = fields[1]
                    else -> return null
                }
            }
            return WatcherStartReport(
                pid = pid,
                uid = uid,
                startEpochMillis = head[3].toLongOrNull(),
                stale = stale.takeIf { scanError == null },
                scanError = scanError,
            )
        }

        /** One word, so a message never breaks the line format. */
        private fun token(value: String): String =
            value.trim().replace(Regex("\\s+"), "_").ifEmpty { "unknown" }.take(MAX_TOKEN_LENGTH)

        private const val MAX_TOKEN_LENGTH = 120
    }
}
