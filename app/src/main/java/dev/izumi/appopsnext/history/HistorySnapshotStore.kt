package dev.izumi.appopsnext.history

import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.history.model.AppOpHistoryEvent
import dev.izumi.appopsnext.presentation.history.ResolvedHistoryEvent
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class HistorySnapshot(
    val events: List<ResolvedHistoryEvent>,
    val fetchedAtMillis: Long,
)

/**
 * Persists the last successfully resolved history for each operation. The presentation layer
 * applies its filters after reading these snapshots, so system-app entries stay available here.
 */
class HistorySnapshotStore(
    private val file: File,
) {
    private val mutex = Mutex()
    private var cache: Map<String, HistorySnapshot>? = null
    private var pendingWrite = false

    suspend fun read(): Map<String, HistorySnapshot> = mutex.withLock {
        cache ?: withContext(Dispatchers.IO) {
            readFromDisk().also { cache = it }
        }
    }

    suspend fun put(
        operationName: String,
        snapshot: HistorySnapshot,
    ) {
        stage(operationName, snapshot)
        flush()
    }

    /**
     * Records a snapshot in memory without touching the disk. Writing serializes every
     * operation at once, so a caller updating several of them stages each one and
     * flushes a single time.
     */
    suspend fun stage(
        operationName: String,
        snapshot: HistorySnapshot,
    ) = mutex.withLock {
        cache = (cache ?: withContext(Dispatchers.IO) { readFromDisk() }) +
            (operationName to snapshot.copy(events = snapshot.events.toList()))
        pendingWrite = true
    }

    /** Persists staged snapshots. Does nothing when there is nothing to write. */
    suspend fun flush() = mutex.withLock {
        val staged = cache?.takeIf { pendingWrite } ?: return@withLock
        withContext(Dispatchers.IO) {
            try {
                if (writeToDisk(staged)) pendingWrite = false
            } catch (_: IOException) {
                // A failed cache write must not discard the usable in-memory snapshot.
            }
        }
    }

    private fun readFromDisk(): Map<String, HistorySnapshot> {
        return try {
            if (!file.isFile || file.length() > MAX_FILE_BYTES) return emptyMap()
            DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
                if (input.readInt() != MAGIC) return emptyMap()
                val version = input.readInt()
                if (version !in 1..VERSION) return emptyMap()
                val operationCount = input.readBoundedCount(MAX_OPERATIONS)
                val snapshots = LinkedHashMap<String, HistorySnapshot>(operationCount)
                var totalEvents = 0
                repeat(operationCount) {
                    val operationName = input.readBoundedString()
                    val fetchedAtMillis = input.readLong()
                    val eventCount = input.readBoundedCount(MAX_EVENTS - totalEvents)
                    totalEvents += eventCount
                    val events = ArrayList<ResolvedHistoryEvent>(eventCount)
                    repeat(eventCount) { events += input.readResolvedEvent(version) }
                    snapshots[operationName] = HistorySnapshot(events, fetchedAtMillis)
                }
                snapshots
            }
        } catch (_: IOException) {
            emptyMap()
        }
    }

    @Throws(IOException::class)
    private fun writeToDisk(snapshots: Map<String, HistorySnapshot>): Boolean {
        if (snapshots.size > MAX_OPERATIONS) return false
        var totalEvents = 0
        snapshots.values.forEach {
            if (it.events.size > MAX_EVENTS - totalEvents) return false
            totalEvents += it.events.size
        }
        val parent = file.parentFile ?: return false
        Files.createDirectories(parent.toPath())
        val temp = File.createTempFile("${file.name}.", ".tmp", parent)
        try {
            DataOutputStream(BufferedOutputStream(BoundedOutputStream(temp.outputStream()))).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                output.writeInt(snapshots.size)
                snapshots.forEach { (operationName, snapshot) ->
                    output.writeBoundedString(operationName)
                    output.writeLong(snapshot.fetchedAtMillis)
                    output.writeInt(snapshot.events.size)
                    snapshot.events.forEach { output.writeResolvedEvent(it) }
                }
            }
            Files.move(temp.toPath(), file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temp.toPath())
        }
        return true
    }

    private fun DataInputStream.readResolvedEvent(version: Int): ResolvedHistoryEvent = ResolvedHistoryEvent(
        event = AppOpHistoryEvent(
            uid = readInt(),
            packageName = readBoundedString(),
            operationName = readBoundedString(),
            attributionTag = readNullableString(),
            accessTimeMillis = readLong(),
            durationMillis = readNullableLong(),
            uidState = readBoundedString(),
            flags = readBoundedString(),
            accessCount = readInt(),
            isAggregated = readBoolean(),
            rejectCount = if (version >= 2) readBoundedCount(Int.MAX_VALUE) else 0,
            intervalStartTimeMillis = if (version >= 2) readNullableLong() else null,
        ),
        app = InstalledApp(
            label = readBoundedString(),
            packageName = readBoundedString(),
            uid = readInt(),
            isSystemApp = readBoolean(),
        ),
    )

    private fun DataOutputStream.writeResolvedEvent(resolved: ResolvedHistoryEvent) {
        val event = resolved.event
        writeInt(event.uid)
        writeBoundedString(event.packageName)
        writeBoundedString(event.operationName)
        writeNullableString(event.attributionTag)
        writeLong(event.accessTimeMillis)
        writeNullableLong(event.durationMillis)
        writeBoundedString(event.uidState)
        writeBoundedString(event.flags)
        writeInt(event.accessCount)
        writeBoolean(event.isAggregated)
        writeInt(event.rejectCount)
        writeNullableLong(event.intervalStartTimeMillis)
        val app = resolved.app
        writeBoundedString(app.label)
        writeBoundedString(app.packageName)
        writeInt(app.uid)
        writeBoolean(app.isSystemApp)
    }

    private fun DataInputStream.readBoundedCount(maximum: Int): Int = readInt().also {
        if (it < 0 || it > maximum) throw IOException("Invalid snapshot count")
    }

    private fun DataInputStream.readBoundedString(): String {
        val byteCount = readBoundedCount(MAX_STRING_BYTES)
        val bytes = ByteArray(byteCount)
        readFully(bytes)
        return bytes.toString(Charsets.UTF_8)
    }

    private fun DataOutputStream.writeBoundedString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_STRING_BYTES) throw IOException("Snapshot string is too large")
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readNullableString(): String? = if (readBoolean()) readBoundedString() else null

    private fun DataOutputStream.writeNullableString(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeBoundedString(value)
    }

    private fun DataInputStream.readNullableLong(): Long? = if (readBoolean()) readLong() else null

    private fun DataOutputStream.writeNullableLong(value: Long?) {
        writeBoolean(value != null)
        if (value != null) writeLong(value)
    }

    private class BoundedOutputStream(output: OutputStream) : FilterOutputStream(output) {
        private var bytesWritten = 0L

        override fun write(value: Int) {
            reserve(1)
            out.write(value)
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            reserve(length)
            out.write(bytes, offset, length)
        }

        private fun reserve(count: Int) {
            bytesWritten += count
            if (bytesWritten > MAX_FILE_BYTES) throw IOException("Snapshot file is too large")
        }
    }

    private companion object {
        const val MAGIC = 0x48534E50 // HSNP
        const val VERSION = 2
        const val MAX_FILE_BYTES = 32L * 1024 * 1024
        const val MAX_OPERATIONS = 512
        const val MAX_EVENTS = 100_000
        const val MAX_STRING_BYTES = 1 * 1024 * 1024
    }
}
