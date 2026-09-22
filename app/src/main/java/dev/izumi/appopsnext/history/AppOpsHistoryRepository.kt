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
            val discreteEvents = discreteParser.parse(
                operationName,
                result.stdout,
            )
            val aggregated = aggregatedParser.parse(operationName, result.stdout)
            // Android's discrete section records accesses, not denied attempts.
            // Keep the aggregate rejections even when discrete accesses exist,
            // without counting the aggregate accesses or durations a second time.
            val events = if (discreteEvents.isEmpty()) aggregated else {
                discreteEvents + aggregated.filter { it.rejectCount > 0 }.map {
                    it.copy(accessCount = 0, durationMillis = null)
                }
            }
            AppOpHistoryLoadResult.Success(
                events = events.sortedByDescending { it.accessTimeMillis },
            )
        }
    }
}
