package dev.izumi.appopsnext.diagnostics

import android.content.Context
import java.io.File
import java.time.Clock
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

class DiagnosticLogRepository internal constructor(
    private val logFile: File,
    private val clock: Clock = Clock.systemDefaultZone(),
    persistenceScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    constructor(context: Context, clock: Clock = Clock.systemDefaultZone()) :
        this(File(context.filesDir, LOG_FILE_NAME), clock)

    private val persistenceRequests =
        Channel<PersistenceRequest>(capacity = Channel.UNLIMITED)
    private val lock = Any()
    private val mutableLines = MutableStateFlow<List<String>>(emptyList())

    @Volatile
    private var discardStoredLines = false

    val lines: StateFlow<List<String>> = mutableLines.asStateFlow()

    init {
        persistenceScope.launch {
            // Reading the existing log here keeps it off the caller's thread, which is
            // the main thread during application start-up. Requests recorded meanwhile
            // are still queued, so the file cannot contain them yet and nothing is
            // duplicated by this merge.
            val persistedLines = ArrayDeque<String>()
            var storedLineCount = restoreStoredLines(persistedLines)
            for (request in persistenceRequests) {
                runCatching {
                    when (request) {
                        is PersistenceRequest.Append -> {
                            logFile.appendText(request.line + "\n")
                            persistedLines.addLast(request.line)
                            while (persistedLines.size > MAX_LINES) persistedLines.removeFirst()
                            storedLineCount++
                            if (storedLineCount > COMPACTION_THRESHOLD_LINES) {
                                storedLineCount = compact(persistedLines)
                            }
                        }

                        is PersistenceRequest.Clear -> {
                            java.nio.file.Files.deleteIfExists(logFile.toPath())
                            persistedLines.clear()
                            storedLineCount = 0
                        }
                    }
                }
            }
        }
    }

    fun info(source: String, message: String) {
        record(DiagnosticLogLevel.INFO, source, message)
    }

    fun warning(source: String, message: String) {
        record(DiagnosticLogLevel.WARNING, source, message)
    }

    fun error(source: String, message: String, error: Throwable? = null) {
        val errorDetails = error?.let {
            " ${it::class.java.simpleName}: ${it.message.orEmpty()}"
        }.orEmpty()
        record(
            level = DiagnosticLogLevel.ERROR,
            source = source,
            message = message + errorDetails,
        )
    }

    fun clear() {
        discardStoredLines = true
        synchronized(lock) {
            mutableLines.value = emptyList()
            persistenceRequests.trySend(PersistenceRequest.Clear)
        }
    }

    private fun record(
        level: DiagnosticLogLevel,
        source: String,
        message: String,
    ) {
        val line = DiagnosticLogFormatter.format(
            timestamp = OffsetDateTime.now(clock),
            level = level,
            source = source,
            message = message,
        )
        synchronized(lock) {
            mutableLines.value =
                (mutableLines.value + line).takeLast(MAX_LINES)
            persistenceRequests.trySend(PersistenceRequest.Append(line))
        }
    }

    /** Merges the persisted log into the state flow and reports the file's line count. */
    private fun restoreStoredLines(persistedLines: ArrayDeque<String>): Int {
        val stored = runCatching {
            if (logFile.isFile) logFile.readLines() else emptyList()
        }.getOrDefault(emptyList())
        if (stored.isEmpty()) return 0
        persistedLines.addAll(stored.takeLast(MAX_LINES))
        synchronized(lock) {
            if (discardStoredLines) return 0
            mutableLines.value =
                (stored.takeLast(MAX_LINES) + mutableLines.value)
                    .takeLast(MAX_LINES)
        }
        return stored.size
    }

    /** Rewrites the file down to the retained window and reports its new line count. */
    private fun compact(retained: ArrayDeque<String>): Int {
        // Only compact events already processed by this writer. UI state may
        // contain newer events that are still queued for append (or a clear).
        if (retained.isEmpty()) {
            logFile.delete()
            return 0
        }
        logFile.writeText(
            retained.joinToString(separator = "\n", postfix = "\n"),
        )
        return retained.size
    }

    private sealed interface PersistenceRequest {
        data class Append(val line: String) : PersistenceRequest

        data object Clear : PersistenceRequest
    }

    private companion object {
        const val LOG_FILE_NAME = "appopsnext-diagnostic.log"
        const val MAX_LINES = 300
        const val COMPACTION_THRESHOLD_LINES = MAX_LINES * 4
    }
}
