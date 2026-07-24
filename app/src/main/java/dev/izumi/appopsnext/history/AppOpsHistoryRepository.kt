package dev.izumi.appopsnext.history

import dev.izumi.appopsnext.appops.PrivilegedAppOpsGateway
import dev.izumi.appopsnext.history.model.AppOpHistoryFailureReason
import dev.izumi.appopsnext.history.model.AppOpHistoryLoadResult
import dev.izumi.appopsnext.history.parser.DiscreteAppOpsHistoryParser

class AppOpsHistoryRepository(
    private val privilegedGateway: PrivilegedAppOpsGateway,
    private val parser: DiscreteAppOpsHistoryParser =
        DiscreteAppOpsHistoryParser(),
) {
    suspend fun loadOperationHistory(
        operationName: String,
    ): AppOpHistoryLoadResult {
        val result = runCatching {
            privilegedGateway.getDiscreteHistory(operationName)
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

        return AppOpHistoryLoadResult.Success(
            events = parser.parse(operationName, result.stdout),
        )
    }
}
