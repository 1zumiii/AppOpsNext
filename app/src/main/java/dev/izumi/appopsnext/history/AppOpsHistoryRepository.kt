package dev.izumi.appopsnext.history

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
        }.getOrElse {
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

        val discreteEvents = discreteParser.parse(
            operationName,
            result.stdout,
        )
        return AppOpHistoryLoadResult.Success(
            events = discreteEvents.ifEmpty {
                aggregatedParser.parse(operationName, result.stdout)
            },
        )
    }
}
