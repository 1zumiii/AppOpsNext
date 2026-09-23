package dev.izumi.appopsnext.history

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.izumi.appopsnext.appops.PrivilegedAppOpsGateway
import dev.izumi.appopsnext.history.model.AppOpHistoryFailureReason
import dev.izumi.appopsnext.history.model.AppOpHistoryLoadResult
import dev.izumi.appopsnext.history.parser.AggregatedAppOpsHistoryParser
import dev.izumi.appopsnext.history.parser.DiscreteAppOpsHistoryParser

class AppOpsHistoryRepository(
    private val privilegedGateway: PrivilegedAppOpsGateway,
    private val discreteParser: DiscreteAppOpsHistoryParser =
        DiscreteAppOpsHistoryParser(),
    private val aggregatedParser: AggregatedAppOpsHistoryParser =
        AggregatedAppOpsHistoryParser(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun loadOperationHistory(
        operationName: String,
    ): AppOpHistoryLoadResult {
        val result = runCatching {
            privilegedGateway.getHistory(operationName)
        }.onFailure { if (it is CancellationException) throw it }.getOrElse {
            return AppOpHistoryLoadResult.Failure(
                AppOpHistoryFailureReason.BACKEND_UNAVAILABLE,
            )
        }

        if (result.timedOut) {
            return AppOpHistoryLoadResult.Failure(
                AppOpHistoryFailureReason.COMMAND_TIMED_OUT,
            )
        }
        if (result.exitCode != 0) {
            return AppOpHistoryLoadResult.Failure(
                AppOpHistoryFailureReason.COMMAND_FAILED,
            )
        }

        return withContext(Dispatchers.Default) {
            val now = clock()
            val windowStart = now - HISTORY_WINDOW_MILLIS
            val discreteEvents = discreteParser.parse(
                operationName,
                result.stdout,
            ).filter { it.accessTimeMillis >= windowStart }
            // Older intervals are never deleted, only widened, and their printed
            // bounds stop being real: the reference device reports some as far
            // back as 1946. Only the window the history page offers is kept.
            val aggregated = aggregatedParser.parse(operationName, result.stdout)
                .filter { it.accessTimeMillis >= windowStart }
            // Individual records cover the last seven days and never contain
            // denied attempts. An interval inside them keeps only its rejections,
            // an older one keeps everything, and one reaching across their start
            // keeps the accesses beyond the records inside it.
            val events = if (discreteEvents.isEmpty()) aggregated else {
                val individualRecordsStart = now - INDIVIDUAL_RECORD_RETENTION_MILLIS
                val records = IntervalRecordSubtraction(discreteEvents)
                discreteEvents + aggregated.mapNotNull {
                    val start = it.intervalStartTimeMillis ?: it.accessTimeMillis
                    when {
                        it.accessTimeMillis <= individualRecordsStart -> it
                        start >= individualRecordsStart -> it.rejectionsOnly()
                        else -> records.remainder(it)
                    }
                }
            }
            AppOpHistoryLoadResult.Success(
                events = events.sortedByDescending { it.accessTimeMillis },
            )
        }
    }

    companion object {
        /** The longest range the history page offers. */
        const val HISTORY_WINDOW_DAYS = 30
        private const val HISTORY_WINDOW_MILLIS = HISTORY_WINDOW_DAYS * 24L * 60 * 60 * 1000

        /** Android's default retention for individual records. */
        const val INDIVIDUAL_RECORD_RETENTION_DAYS = 7
        private const val INDIVIDUAL_RECORD_RETENTION_MILLIS =
            INDIVIDUAL_RECORD_RETENTION_DAYS * 24L * 60 * 60 * 1000
    }
}
