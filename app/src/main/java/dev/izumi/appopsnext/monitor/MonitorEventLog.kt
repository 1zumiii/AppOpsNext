package dev.izumi.appopsnext.monitor

import dev.izumi.appopsnext.history.readBoundedString
import dev.izumi.appopsnext.history.writeBoundedString
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One access a monitoring point was told about, whatever its settings made of it. */
data class MonitorLogEntry(
    val timeMillis: Long,
    val uid: Int,
    val packageName: String,
    val operationName: String,
    val allowed: Boolean,
)

/**
 * Every access reported to a monitoring point, kept on this device.
 *
 * Recording is separate from reporting: an outcome filter, an interval or the
 * off-screen setting decide what interrupts, not what is written down. The one
 * thing folded is the platform reporting a single access twice within
 * [DUPLICATE_MILLIS], as the notification does.
 *
 * Callbacks can arrive several times a second, so they are collected in memory
 * and appended to the file in batches rather than one write each.
 */
class MonitorEventLog(
    private val file: File,
    private val scope: CoroutineScope,
    private val maxEntries: Int = MAX_ENTRIES,
    private val batchDelayMillis: Long = BATCH_DELAY_MILLIS,
) {
    private val mutex = Mutex()
    private val lock = Any()
    private val pending = mutableListOf<MonitorLogEntry>()
    private val lastRecorded = HashMap<DuplicateKey, Long>()
    private val flushRequests = Channel<Unit>(Channel.CONFLATED)
    private val mutableEntries = MutableStateFlow<List<MonitorLogEntry>?>(null)
    /** Records in the file, which may exceed [maxEntries] until it is compacted. */
    private var storedCount = 0
    /** Set after a failed write, whose file can no longer be appended to safely. */
    private var rewriteNeeded = false

    /** Oldest first. Null until [load] has read the file. */
    val entries: StateFlow<List<MonitorLogEntry>?> = mutableEntries.asStateFlow()

    init {
        scope.launch {
            while (true) {
                flushRequests.receive()
                delay(batchDelayMillis)
                flush()
            }
        }
    }

    suspend fun load() {
        mutex.withLock { current() }
    }

    /** Called from the monitor's event loop; never blocks on the disk. */
    fun record(entry: MonitorLogEntry, elapsedRealtimeMillis: Long) {
        val key = DuplicateKey(entry.uid, entry.packageName, entry.operationName, entry.allowed)
        synchronized(lock) {
            val last = lastRecorded[key]
            // A clock that has gone backwards cannot be used to fold an access.
            if (last != null && elapsedRealtimeMillis - last in 0 until DUPLICATE_MILLIS) return
            lastRecorded[key] = elapsedRealtimeMillis
            if (lastRecorded.size > MAX_DUPLICATE_KEYS) lastRecorded.clear()
            pending += entry
        }
        flushRequests.trySend(Unit)
    }

    suspend fun clear() = mutex.withLock {
        synchronized(lock) { pending.clear() }
        withContext(Dispatchers.IO) {
            try {
                Files.deleteIfExists(file.toPath())
                storedCount = 0
            } catch (_: IOException) {
                // What cannot be deleted is overwritten by the next write instead.
                rewriteNeeded = true
            }
        }
        mutableEntries.value = emptyList()
    }

    suspend fun flush() = mutex.withLock {
        val batch = synchronized(lock) { pending.toList().also { pending.clear() } }
        if (batch.isEmpty()) return@withLock
        val all = (current() + batch).takeLast(maxEntries)
        mutableEntries.value = all
        withContext(Dispatchers.IO) {
            try {
                // Appending keeps a burst cheap; the file is rewritten only once it
                // holds half as many records again as are kept.
                if (rewriteNeeded || !file.isFile || storedCount + batch.size > maxEntries + maxEntries / 2) {
                    rewrite(all)
                } else {
                    append(batch)
                }
            } catch (_: IOException) {
                // The records stay in memory; the next batch rewrites the file.
                rewriteNeeded = true
            }
        }
    }

    private suspend fun current(): List<MonitorLogEntry> =
        mutableEntries.value ?: withContext(Dispatchers.IO) { readFromDisk() }.also {
            mutableEntries.value = it
        }

    /**
     * Keeps every complete record. A write cut short by the process ending leaves
     * a partial one at the end, which is dropped and the file rewritten without
     * it, so later appends do not follow the fragment. A file that is not a log
     * of this version is replaced: it holds nothing that can be shown.
     */
    private fun readFromDisk(): List<MonitorLogEntry> {
        if (!file.isFile) return emptyList()
        val entries = ArrayList<MonitorLogEntry>()
        var complete = true
        try {
            DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                    complete = false
                    return@use
                }
                while (true) {
                    val time = input.readTimeOrNull() ?: break
                    entries += MonitorLogEntry(
                        timeMillis = time,
                        uid = input.readInt(),
                        packageName = input.readBoundedString(),
                        operationName = input.readBoundedString(),
                        allowed = input.readBoolean(),
                    )
                }
            }
        } catch (_: IOException) {
            complete = false
        }
        val kept = entries.takeLast(maxEntries)
        storedCount = entries.size
        if (!complete || kept.size < entries.size) {
            try {
                rewrite(kept)
            } catch (_: IOException) {
                rewriteNeeded = true
            }
        }
        return kept
    }

    @Throws(IOException::class)
    private fun append(batch: List<MonitorLogEntry>) {
        DataOutputStream(BufferedOutputStream(FileOutputStream(file, true))).use { output ->
            batch.forEach { output.writeEntry(it) }
        }
        storedCount += batch.size
    }

    @Throws(IOException::class)
    private fun rewrite(entries: List<MonitorLogEntry>) {
        val parent = file.parentFile ?: throw IOException("Log file has no directory")
        Files.createDirectories(parent.toPath())
        val temp = File.createTempFile("${file.name}.", ".tmp", parent)
        try {
            DataOutputStream(BufferedOutputStream(temp.outputStream())).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                entries.forEach { output.writeEntry(it) }
            }
            Files.move(temp.toPath(), file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temp.toPath())
        }
        storedCount = entries.size
        rewriteNeeded = false
    }

    /**
     * Null only at a clean end of the file. A record cut short anywhere, even
     * inside its first field, throws, so the caller rewrites the file without it.
     */
    private fun DataInputStream.readTimeOrNull(): Long? {
        val first = read()
        if (first < 0) return null
        var value = first.toLong()
        repeat(Long.SIZE_BYTES - 1) { value = (value shl 8) or readUnsignedByte().toLong() }
        return value
    }

    private fun DataOutputStream.writeEntry(entry: MonitorLogEntry) {
        writeLong(entry.timeMillis)
        writeInt(entry.uid)
        writeBoundedString(entry.packageName)
        writeBoundedString(entry.operationName)
        writeBoolean(entry.allowed)
    }

    private data class DuplicateKey(
        val uid: Int,
        val packageName: String,
        val operationName: String,
        val allowed: Boolean,
    )

    companion object {
        const val MAX_ENTRIES = 50_000
        /** The platform reports one access twice, four to ten milliseconds apart. */
        private const val DUPLICATE_MILLIS = MonitorAccessAccumulator.DUPLICATE_CALLBACK_MILLIS
        private const val BATCH_DELAY_MILLIS = 2_000L
        private const val MAX_DUPLICATE_KEYS = 1_024
        private const val MAGIC = 0x4D4C4F47 // MLOG
        private const val VERSION = 1
    }
}
