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

class DiagnosticLogRepository(
    context: Context,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val logFile = File(context.filesDir, LOG_FILE_NAME)
    private val persistenceRequests =
        Channel<List<String>>(capacity = Channel.UNLIMITED)
    private val persistenceScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val mutableLines = MutableStateFlow(loadExistingLines())

    val lines: StateFlow<List<String>> = mutableLines.asStateFlow()

    init {
        persistenceScope.launch {
            for (snapshot in persistenceRequests) {
                runCatching {
                    if (snapshot.isEmpty()) {
                        logFile.delete()
                    } else {
                        logFile.writeText(
                            snapshot.joinToString(
                                separator = "\n",
                                postfix = "\n",
                            ),
                        )
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
        synchronized(lock) {
            mutableLines.value = emptyList()
            persistenceRequests.trySend(emptyList())
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
            val updated =
                (mutableLines.value + line).takeLast(MAX_LINES)
            mutableLines.value = updated
            persistenceRequests.trySend(updated)
        }
    }

    private fun loadExistingLines(): List<String> =
        runCatching {
            if (logFile.isFile) {
                logFile.readLines().takeLast(MAX_LINES)
            } else {
                emptyList()
            }
        }.getOrDefault(emptyList())

    private companion object {
        const val LOG_FILE_NAME = "appopsnext-diagnostic.log"
        const val MAX_LINES = 300
    }
}
