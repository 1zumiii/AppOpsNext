package dev.izumi.appopsnext.diagnostics

import android.content.Context
import dev.izumi.appopsnext.diagnostics.DiagnosticLogLines.Entry
import dev.izumi.appopsnext.diagnostics.DiagnosticLogLines.toEntries
import java.io.File
import java.io.RandomAccessFile
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DiagnosticLogLevel {
    INFO,
    WARNING,
    ERROR,
}

/**
 * Which share of the log a line counts against. Routine lines come often and
 * say little on their own, so they have a quota of their own and cannot push
 * out the lines a report is usually read for. Every warning and error is
 * important; an INFO line is important only when its caller says so.
 */
enum class DiagnosticLogTier {
    IMPORTANT,
    ROUTINE,
}

object DiagnosticLogFormatter {
    fun format(
        timestamp: OffsetDateTime,
        level: DiagnosticLogLevel,
        source: String,
        message: String,
    ): String {
        val normalizedSource = normalize(source, MAX_SOURCE_LENGTH)
        val normalizedMessage = normalize(message, MAX_MESSAGE_LENGTH)
        return buildString {
            append(timestamp.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            append(" [")
            append(level.name)
            append("] [")
            append(normalizedSource)
            append("] ")
            append(normalizedMessage)
        }
    }

    private fun normalize(value: String, maximumLength: Int): String =
        value
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifEmpty { "-" }
            .take(maximumLength)

    private const val MAX_SOURCE_LENGTH = 80
    private const val MAX_MESSAGE_LENGTH = 1_200
}

/**
 * The in-app diagnostic log. Each tier lives in a file of its own and keeps
 * its own number of lines; [lines] and the report show both merged by time.
 * The routine file keeps the name the single log had, so a log written before
 * the tiers still loads: its warnings and errors move to the important file.
 */
class DiagnosticLogRepository internal constructor(
    private val logFile: File,
    private val clock: Clock = Clock.systemDefaultZone(),
    persistenceScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    constructor(context: Context, clock: Clock = Clock.systemDefaultZone()) :
        this(File(context.filesDir, LOG_FILE_NAME), clock)

    private val importantFile = sibling("important", logFile.extension)
    private val marksFile = sibling("marks", "properties")

    private val persistenceRequests =
        Channel<PersistenceRequest>(capacity = Channel.UNLIMITED)
    private val lock = Any()
    private val mutableLines = MutableStateFlow<List<String>>(emptyList())

    // Guarded by lock.
    private val memory = mapOf(
        DiagnosticLogTier.IMPORTANT to MemoryTier(IMPORTANT_MAX_LINES),
        DiagnosticLogTier.ROUTINE to MemoryTier(ROUTINE_MAX_LINES),
    )
    private var nextSequence = 0L
    private var marks: MutableMap<String, String>? = null

    @Volatile
    private var discardStoredLines = false

    val lines: StateFlow<List<String>> = mutableLines.asStateFlow()

    init {
        persistenceScope.launch {
            // Reading the existing log here keeps it off the caller's thread, which is
            // the main thread during application start-up. Requests recorded meanwhile
            // are still queued, so the files cannot contain them yet and nothing is
            // duplicated by this merge.
            val stored = mapOf(
                DiagnosticLogTier.IMPORTANT to StoredTier(importantFile, IMPORTANT_MAX_LINES),
                DiagnosticLogTier.ROUTINE to StoredTier(logFile, ROUTINE_MAX_LINES),
            )
            runCatching { restoreStoredLines(stored) }
            for (request in persistenceRequests) {
                runCatching {
                    when (request) {
                        is PersistenceRequest.Append -> stored.getValue(request.tier).append(request.line)
                        is PersistenceRequest.ReplaceLast -> stored.getValue(request.tier).replaceLast(request.line)
                        is PersistenceRequest.SaveMarks -> saveMarks(request.marks)
                        is PersistenceRequest.Clear -> {
                            stored.values.forEach(StoredTier::clear)
                            java.nio.file.Files.deleteIfExists(marksFile.toPath())
                        }
                    }
                }
            }
        }
    }

    /** [tier] lets a caller keep an INFO line with the warnings, as for a process start. */
    fun info(
        source: String,
        message: String,
        tier: DiagnosticLogTier = DiagnosticLogTier.ROUTINE,
    ) {
        record(DiagnosticLogLevel.INFO, source, message, tier)
    }

    fun warning(source: String, message: String) {
        record(DiagnosticLogLevel.WARNING, source, message, DiagnosticLogTier.IMPORTANT)
    }

    fun error(source: String, message: String, error: Throwable? = null) {
        val errorDetails = error?.let {
            " ${it::class.java.simpleName}: ${it.message.orEmpty()}"
        }.orEmpty()
        record(
            level = DiagnosticLogLevel.ERROR,
            source = source,
            message = message + errorDetails,
            tier = DiagnosticLogTier.IMPORTANT,
        )
    }

    /**
     * Notes [value] under [key] and reports whether it differs from the value
     * noted before, so a caller can write a line only when what it says changed.
     * The notes outlive the process, which is what keeps every start from
     * repeating the same line, and are dropped with the log by [clear].
     */
    fun noteIfChanged(key: String, value: String): Boolean {
        synchronized(lock) {
            // Read on first use: a few short lines, and only callers that need them pay.
            val current = marks ?: loadMarks().also { marks = it }
            if (current[key] == value) return false
            current[key] = value
            persistenceRequests.trySend(PersistenceRequest.SaveMarks(current.toMap()))
            return true
        }
    }

    fun clear() {
        discardStoredLines = true
        synchronized(lock) {
            memory.values.forEach(MemoryTier::clear)
            marks = mutableMapOf()
            mutableLines.value = emptyList()
            persistenceRequests.trySend(PersistenceRequest.Clear)
        }
    }

    private fun record(
        level: DiagnosticLogLevel,
        source: String,
        message: String,
        tier: DiagnosticLogTier,
    ) {
        val now = OffsetDateTime.now(clock)
        val line = DiagnosticLogFormatter.format(
            timestamp = now,
            level = level,
            source = source,
            message = message,
        )
        val foldable = DiagnosticLogLines.foldable(source, message)
        synchronized(lock) {
            val target = memory.getValue(tier)
            val folded = if (foldable) target.fold(line, now) else null
            if (folded != null) {
                persistenceRequests.trySend(PersistenceRequest.ReplaceLast(tier, folded))
            } else {
                target.append(Entry(line, now.toInstant(), nextSequence++), now, foldable)
                persistenceRequests.trySend(PersistenceRequest.Append(tier, line))
            }
            publish()
        }
    }

    // Called with lock held.
    private fun publish() {
        mutableLines.value = DiagnosticLogLines.merge(
            memory.getValue(DiagnosticLogTier.IMPORTANT).entries,
            memory.getValue(DiagnosticLogTier.ROUTINE).entries,
        ).map(Entry::line)
    }

    /** Merges the persisted log into the state flow and readies the files for appends. */
    private fun restoreStoredLines(stored: Map<DiagnosticLogTier, StoredTier>) {
        val important = stored.getValue(DiagnosticLogTier.IMPORTANT)
        val routine = stored.getValue(DiagnosticLogTier.ROUTINE)
        val importantText = important.readText()
        val routineText = routine.readText()
        // A routine file holds warnings only when it was written before the tiers.
        val (moved, routineLines) = routineText.lines
            .partition { DiagnosticLogLines.storedTierOf(it) == DiagnosticLogTier.IMPORTANT }
        val importantLines = if (moved.isEmpty()) {
            importantText.lines
        } else {
            DiagnosticLogLines.mergeLines(importantText.lines, moved)
        }
        if (importantLines.isEmpty() && routineLines.isEmpty()) return
        synchronized(lock) {
            if (discardStoredLines) return
            var sequence = -(importantLines.size + routineLines.size).toLong()
            memory.getValue(DiagnosticLogTier.IMPORTANT)
                .prepend(importantLines.takeLast(IMPORTANT_MAX_LINES).toEntries(sequence))
            sequence += importantLines.size
            memory.getValue(DiagnosticLogTier.ROUTINE)
                .prepend(routineLines.takeLast(ROUTINE_MAX_LINES).toEntries(sequence))
            publish()
        }
        important.restore(importantLines, importantText.size, rewrite = moved.isNotEmpty() || !importantText.clean)
        routine.restore(routineLines, routineText.size, rewrite = moved.isNotEmpty() || !routineText.clean)
    }

    private fun loadMarks(): MutableMap<String, String> {
        val text = runCatching { if (marksFile.isFile) marksFile.readLines() else emptyList() }
            .getOrDefault(emptyList())
        return text.mapNotNull { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1)
        }.toMap(mutableMapOf())
    }

    private fun saveMarks(snapshot: Map<String, String>) {
        marksFile.writeText(snapshot.entries.joinToString(separator = "") { "${it.key}=${it.value}\n" })
    }

    private fun sibling(suffix: String, extension: String): File {
        val name = logFile.nameWithoutExtension + "-" + suffix +
            (if (extension.isEmpty()) "" else ".$extension")
        return File(logFile.parentFile, name)
    }

    /** One tier as the state flow shows it. */
    private class MemoryTier(private val maxLines: Int) {
        val entries = ArrayDeque<Entry>()
        private var fold: Fold? = null

        /**
         * Folds [line] into the last one when they say the same within
         * [DiagnosticLogLines.FOLD_WINDOW] of its first time, and returns the
         * folded line, or null when [line] stands on its own.
         */
        fun fold(line: String, now: OffsetDateTime): String? {
            val current = fold ?: return null
            if (entries.isEmpty() || current.key != keyOf(line)) return null
            if (now.isBefore(current.firstAt) ||
                Duration.between(current.firstAt, now) > DiagnosticLogLines.FOLD_WINDOW
            ) {
                return null
            }
            current.count++
            val folded = current.firstLine + DiagnosticLogLines.foldSuffix(current.count, now)
            entries[entries.lastIndex] = entries.last().copy(line = folded)
            return folded
        }

        fun append(entry: Entry, at: OffsetDateTime, foldable: Boolean) {
            entries.addLast(entry)
            while (entries.size > maxLines) entries.removeFirst()
            fold = if (foldable) Fold(keyOf(entry.line), at, entry.line) else null
        }

        fun prepend(stored: List<Entry>) {
            val combined = stored + entries
            entries.clear()
            entries.addAll(combined.takeLast(maxLines))
        }

        fun clear() {
            entries.clear()
            fold = null
        }

        /** The level, source and message: everything but the time. */
        private fun keyOf(line: String): String = line.substringAfter(' ')

        private class Fold(val key: String, val firstAt: OffsetDateTime, val firstLine: String) {
            var count = 1
        }
    }

    /** One tier's file, touched only by the persistence coroutine. */
    private class StoredTier(private val file: File, private val maxLines: Int) {
        private val retained = ArrayDeque<String>()
        private var storedLineCount = 0

        /** The bytes of the file's last line and its newline, or -1 when not known. */
        private var lastLineBytes = -1

        class Text(val lines: List<String>, val size: Int, val clean: Boolean)

        fun readText(): Text {
            val text = runCatching { if (file.isFile) file.readText() else "" }.getOrDefault("")
            val lines = text.lines().filter(String::isNotEmpty)
            // A file cut off mid-line would join the next append onto that line.
            return Text(lines, lines.size, clean = text.isEmpty() || text.endsWith("\n"))
        }

        fun restore(lines: List<String>, storedLines: Int, rewrite: Boolean) {
            retained.clear()
            retained.addAll(lines.takeLast(maxLines))
            storedLineCount = storedLines
            if (rewrite) {
                compact()
            } else {
                lastLineBytes = retained.lastOrNull()?.let(::bytesOf) ?: -1
            }
        }

        fun append(line: String) {
            file.appendText(line + "\n")
            retained.addLast(line)
            while (retained.size > maxLines) retained.removeFirst()
            lastLineBytes = bytesOf(line)
            storedLineCount++
            if (storedLineCount > maxLines * COMPACTION_FACTOR) compact()
        }

        /** Rewrites only the last line, which is what a fold changes. */
        fun replaceLast(line: String) {
            if (retained.isEmpty()) {
                append(line)
                return
            }
            retained[retained.lastIndex] = line
            val length = file.length()
            if (lastLineBytes <= 0 || length < lastLineBytes) {
                compact()
                return
            }
            RandomAccessFile(file, "rw").use { access ->
                access.setLength(length - lastLineBytes)
                access.seek(length - lastLineBytes)
                access.write((line + "\n").toByteArray(Charsets.UTF_8))
            }
            lastLineBytes = bytesOf(line)
        }

        fun clear() {
            java.nio.file.Files.deleteIfExists(file.toPath())
            retained.clear()
            storedLineCount = 0
            lastLineBytes = -1
        }

        /** Rewrites the file down to the retained window. */
        private fun compact() {
            // Only compact events already processed by this writer. UI state may
            // contain newer events that are still queued for append (or a clear).
            if (retained.isEmpty()) {
                file.delete()
                storedLineCount = 0
                lastLineBytes = -1
                return
            }
            file.writeText(retained.joinToString(separator = "\n", postfix = "\n"))
            storedLineCount = retained.size
            lastLineBytes = bytesOf(retained.last())
        }

        private fun bytesOf(line: String): Int = line.toByteArray(Charsets.UTF_8).size + 1
    }

    private sealed interface PersistenceRequest {
        data class Append(val tier: DiagnosticLogTier, val line: String) : PersistenceRequest

        data class ReplaceLast(val tier: DiagnosticLogTier, val line: String) : PersistenceRequest

        data class SaveMarks(val marks: Map<String, String>) : PersistenceRequest

        data object Clear : PersistenceRequest
    }

    private companion object {
        const val LOG_FILE_NAME = "appopsnext-diagnostic.log"

        /**
         * Starts, connections, lifecycle and every warning. These are few, so
         * this share reaches back much further than the routine one.
         */
        const val IMPORTANT_MAX_LINES = 200
        const val ROUTINE_MAX_LINES = 300
        const val COMPACTION_FACTOR = 4
    }
}
