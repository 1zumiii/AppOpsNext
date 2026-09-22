package dev.izumi.appopsnext.history

import dev.izumi.appopsnext.presentation.history.ResolvedHistoryEvent
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Individual records kept past the system's seven days, with the periods they
 * cover. A covered period needs no interval counts for its accesses; a gap does.
 */
data class ArchivedHistory(
    val events: List<ResolvedHistoryEvent>,
    /** Sorted, non-overlapping [start, end] periods in epoch milliseconds. */
    val coverage: List<LongRange>,
)

/** Why the saved records on disk are not what the archive holds. */
enum class HistoryArchiveProblem {
    /**
     * The file exists but could not be read: damaged, or written by a newer
     * version. It is the only copy of those records, so it is left as it is and
     * nothing is saved over it until the user deletes it.
     */
    UNREADABLE,

    /** The last write did not reach the disk. The records stay in memory and the next flush retries. */
    WRITE_FAILED,
}

/**
 * Saved only while the user has enabled it, and only for operations the system
 * keeps individual records for. Nothing here is ever read back into AppOps.
 */
class HistoryArchiveStore(
    private val file: File,
    private val maxEvents: Int = MAX_EVENTS,
    private val maxFileBytes: Long = MAX_FILE_BYTES,
) {
    private val mutex = Mutex()
    private val mutableContents = MutableStateFlow<Map<String, ArchivedHistory>?>(null)
    private val mutableProblem = MutableStateFlow<HistoryArchiveProblem?>(null)
    private var pendingWrite = false

    /** Null until [load] has read the file. */
    val contents: StateFlow<Map<String, ArchivedHistory>?> = mutableContents.asStateFlow()

    val problem: StateFlow<HistoryArchiveProblem?> = mutableProblem.asStateFlow()

    private val unreadable: Boolean
        get() = mutableProblem.value == HistoryArchiveProblem.UNREADABLE

    suspend fun load() {
        mutex.withLock { current() }
    }

    /**
     * Adds the records not already saved and extends the coverage by the period
     * the system's own records spanned when they were read.
     *
     * Several accesses in one minute are identical records, so a record is new
     * only beyond the number of copies already saved, never merely by its content.
     */
    suspend fun record(
        operationName: String,
        events: List<ResolvedHistoryEvent>,
        coveredFrom: Long,
        coveredTo: Long,
    ) = mutex.withLock {
        val all = current()
        if (unreadable) return@withLock
        val updated = withContext(Dispatchers.Default) {
            val existing = all[operationName]
            val added = newRecords(existing?.events.orEmpty(), events)
            val coverage = mergeCoverage(existing?.coverage.orEmpty() + listOf(coveredFrom..coveredTo))
            if (added.isEmpty() && coverage == existing?.coverage) {
                null
            } else {
                trimToLimit(all + (operationName to ArchivedHistory(existing?.events.orEmpty() + added, coverage)), maxEvents)
            }
        } ?: return@withLock
        mutableContents.value = updated
        pendingWrite = true
    }

    /**
     * Removes the records in [fromMillis, toMillis) and their coverage, so the
     * period reads as a gap afterwards, filled from interval counts where any exist.
     */
    suspend fun deleteBetween(fromMillis: Long, toMillis: Long) {
        mutex.withLock {
            val all = current()
            // Records that cannot be read cannot be removed selectively either.
            if (unreadable) return@withLock
            mutableContents.value = all.mapValues { (_, archived) ->
                ArchivedHistory(
                    events = archived.events.filter { it.event.accessTimeMillis !in fromMillis until toMillis },
                    coverage = archived.coverage.flatMap { range ->
                        listOfNotNull(
                            (range.first..minOf(range.last, fromMillis - 1)).takeUnless { it.isEmpty() },
                            (maxOf(range.first, toMillis)..range.last).takeUnless { it.isEmpty() },
                        )
                    },
                )
            }.filterValues { it.events.isNotEmpty() || it.coverage.isNotEmpty() }
            pendingWrite = true
        }
        flush()
    }

    /** Records in [fromMillis, toMillis), to state what a deletion will remove. */
    fun countBetween(fromMillis: Long, toMillis: Long): Int =
        contents.value.orEmpty().values.sumOf { archived ->
            archived.events.count { it.event.accessTimeMillis in fromMillis until toMillis }
        }

    /** Also the only way to discard an unreadable file, after which saving resumes. */
    suspend fun deleteAll() {
        mutex.withLock {
            mutableContents.value = emptyMap()
            if (unreadable) mutableProblem.value = null
            pendingWrite = true
        }
        flush()
    }

    suspend fun flush() = mutex.withLock {
        if (unreadable) return@withLock
        val staged = mutableContents.value?.takeIf { pendingWrite } ?: return@withLock
        withContext(Dispatchers.IO) {
            try {
                if (staged.isEmpty()) {
                    Files.deleteIfExists(file.toPath())
                } else {
                    val written = writeFitting(staged)
                    if (written !== staged) mutableContents.value = written
                }
                pendingWrite = false
                mutableProblem.value = null
            } catch (_: IOException) {
                // The in-memory records stay usable; the next flush tries again.
                mutableProblem.value = HistoryArchiveProblem.WRITE_FAILED
            }
        }
    }

    private suspend fun current(): Map<String, ArchivedHistory> =
        mutableContents.value ?: withContext(Dispatchers.IO) { readFromDisk() }.let { read ->
            if (read == null) mutableProblem.value = HistoryArchiveProblem.UNREADABLE
            (read ?: emptyMap()).also { mutableContents.value = it }
        }

    /**
     * The incoming records beyond the copies already saved. The access time is
     * part of the key, so only saved records inside the incoming span can match,
     * which spares hashing the whole archive on every read.
     */
    private fun newRecords(
        saved: List<ResolvedHistoryEvent>,
        events: List<ResolvedHistoryEvent>,
    ): List<ResolvedHistoryEvent> {
        val incoming = events.filterNot { it.event.isAggregated }
        if (incoming.isEmpty()) return emptyList()
        val from = incoming.minOf { it.event.accessTimeMillis }
        val to = incoming.maxOf { it.event.accessTimeMillis }
        val copies = HashMap<ArchiveKey, Int>()
        saved.forEach { if (it.event.accessTimeMillis in from..to) copies.merge(it.archiveKey(), 1, Int::plus) }
        return incoming.filter { copies.takeCopy(it.archiveKey()).not() }
    }

    /**
     * A file past the size its reader accepts loses its oldest records until it
     * fits, the way a full archive does, instead of failing every write from then on.
     */
    @Throws(IOException::class)
    private fun writeFitting(archive: Map<String, ArchivedHistory>): Map<String, ArchivedHistory> {
        var candidate = archive
        while (true) {
            try {
                writeToDisk(candidate)
                return candidate
            } catch (tooLarge: HistoryFileTooLargeException) {
                val total = candidate.values.sumOf { it.events.size }
                if (total == 0) throw tooLarge
                candidate = trimToLimit(candidate, total - maxOf(1, total / TRIM_DIVISOR))
            }
        }
    }

    /**
     * The oldest records go first once the archive is full, down to exactly the
     * limit: the reader rejects a file holding more, so records sharing the
     * boundary timestamp are split by order rather than all kept.
     */
    private fun trimToLimit(archive: Map<String, ArchivedHistory>, limit: Int): Map<String, ArchivedHistory> {
        val total = archive.values.sumOf { it.events.size }
        if (total <= limit) return archive
        val dropped = archive.flatMap { (operation, archived) ->
            archived.events.mapIndexed { index, event -> Triple(operation, index, event.event.accessTimeMillis) }
        }.sortedBy { it.third }.take(total - limit)
        val droppedIndices = dropped.groupBy({ it.first }, { it.second }).mapValues { it.value.toHashSet() }
        // A timestamp that lost records is no longer fully covered.
        val coveredFrom = dropped.last().third + 1
        return archive.mapValues { (operation, archived) ->
            val drop = droppedIndices[operation].orEmpty()
            ArchivedHistory(
                events = archived.events.filterIndexed { index, _ -> index !in drop },
                coverage = archived.coverage.mapNotNull { range ->
                    (maxOf(range.first, coveredFrom)..range.last).takeUnless { it.isEmpty() }
                },
            )
        }
    }

    /** Empty when there is no file yet, null when there is one that cannot be read. */
    private fun readFromDisk(): Map<String, ArchivedHistory>? = try {
        if (!file.exists()) {
            emptyMap()
        } else if (!file.isFile || file.length() > maxFileBytes) {
            null
        } else {
            DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
                if (input.readInt() != MAGIC) return null
                val layout = when (input.readInt()) {
                    // The first version always wrote the second event layout.
                    1 -> 2
                    VERSION -> input.readInt()
                    else -> return null
                }
                if (layout !in 1..HISTORY_EVENT_LAYOUT_VERSION) return null
                val strings = HashMap<String, String>()
                val archive = LinkedHashMap<String, ArchivedHistory>()
                var totalEvents = 0
                repeat(input.readBoundedCount(MAX_OPERATIONS)) {
                    val operationName = input.readBoundedString()
                    val coverage = List(input.readBoundedCount(MAX_COVERAGE_RANGES)) {
                        input.readLong()..input.readLong()
                    }
                    val eventCount = input.readBoundedCount(maxEvents - totalEvents)
                    totalEvents += eventCount
                    val events = List(eventCount) { input.readHistoryEvent(layout, strings) }
                    archive[operationName] = ArchivedHistory(events, mergeCoverage(coverage))
                }
                archive
            }
        }
    } catch (_: IOException) {
        null
    } catch (_: RuntimeException) {
        null
    }

    @Throws(IOException::class)
    private fun writeToDisk(archive: Map<String, ArchivedHistory>) {
        if (archive.size > MAX_OPERATIONS) throw IOException("Too many archived operations")
        val parent = file.parentFile ?: throw IOException("Archive file has no directory")
        Files.createDirectories(parent.toPath())
        val temp = File.createTempFile("${file.name}.", ".tmp", parent)
        try {
            DataOutputStream(BufferedOutputStream(BoundedOutputStream(temp.outputStream(), maxFileBytes))).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                output.writeInt(HISTORY_EVENT_LAYOUT_VERSION)
                output.writeInt(archive.size)
                archive.forEach { (operationName, archived) ->
                    output.writeBoundedString(operationName)
                    val coverage = archived.coverage.takeLast(MAX_COVERAGE_RANGES)
                    output.writeInt(coverage.size)
                    coverage.forEach {
                        output.writeLong(it.first)
                        output.writeLong(it.last)
                    }
                    output.writeInt(archived.events.size)
                    archived.events.forEach { output.writeHistoryEvent(it) }
                }
            }
            Files.move(temp.toPath(), file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temp.toPath())
        }
    }

    internal companion object {
        const val MAX_EVENTS = 100_000
        private const val MAGIC = 0x48415243 // HARC
        /** Version 2 records the event layout after the version. */
        private const val VERSION = 2
        private const val MAX_FILE_BYTES = 32L * 1024 * 1024
        private const val MAX_OPERATIONS = 512
        private const val MAX_COVERAGE_RANGES = 4096
        /** An oversized write retries with a tenth fewer records each time. */
        private const val TRIM_DIVISOR = 10

        fun mergeCoverage(ranges: List<LongRange>): List<LongRange> =
            ranges.filterNot(LongRange::isEmpty).sortedBy(LongRange::first).fold(mutableListOf()) { merged, range ->
                val last = merged.lastOrNull()
                if (last != null && range.first <= last.last) {
                    merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
                } else {
                    merged += range
                }
                merged
            }
    }
}

/** Identical for copies of one record across reads, and for accesses in one minute. */
internal data class ArchiveKey(
    val uid: Int,
    val packageName: String,
    val operationName: String,
    val attributionTag: String?,
    val accessTimeMillis: Long,
    val uidState: String,
    val flags: String,
)

internal fun ResolvedHistoryEvent.archiveKey(): ArchiveKey = ArchiveKey(
    event.uid, event.packageName, event.operationName, event.attributionTag,
    event.accessTimeMillis, event.uidState, event.flags,
)

/** Uses up one remaining copy of [key], reporting whether there was one. */
internal fun MutableMap<ArchiveKey, Int>.takeCopy(key: ArchiveKey): Boolean {
    val remaining = this[key] ?: return false
    if (remaining == 1) remove(key) else this[key] = remaining - 1
    return true
}
