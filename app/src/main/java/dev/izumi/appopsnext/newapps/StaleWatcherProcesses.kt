package dev.izumi.appopsnext.newapps

import java.io.File

/**
 * Finds watcher processes left behind by an earlier start. Shizuku forgets a
 * user service that takes too long to start without ending its process, and a
 * forgotten watcher would keep waking the app next to the current one.
 */
internal object StaleWatcherProcesses {
    /**
     * The PIDs under [procDir] whose command line names [processName], other than
     * [ownPid]. [ownedBy] limits the result to processes of the caller's own UID,
     * so a process that merely carries the name is never returned.
     */
    fun find(
        procDir: File,
        processName: String,
        ownPid: Int,
        ownedBy: (File) -> Boolean,
    ): List<Int> = procDir.listFiles().orEmpty().mapNotNull { entry ->
        val pid = entry.name.toIntOrNull()?.takeIf { it != ownPid } ?: return@mapNotNull null
        // A process can exit between the listing and the read.
        val name = runCatching { commandName(File(entry, "cmdline").readBytes()) }.getOrNull()
        pid.takeIf { name == processName && runCatching { ownedBy(entry) }.getOrDefault(false) }
    }.sorted()

    /** The first argument of a `cmdline` file, which the platform pads with NUL bytes. */
    fun commandName(cmdline: ByteArray): String {
        val end = cmdline.indexOf(0).let { if (it < 0) cmdline.size else it }
        return String(cmdline, 0, end, Charsets.UTF_8)
    }
}
