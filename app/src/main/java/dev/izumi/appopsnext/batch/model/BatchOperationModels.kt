package dev.izumi.appopsnext.batch.model

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.appops.model.AppOpScope

data class BatchOperationTarget(
    val packageName: String,
    val appLabel: String,
    val stableOperationName: String,
    val scope: AppOpScope,
    val requestedMode: AppOpMode,
)

data class BatchOperationItemResult(
    val target: BatchOperationTarget,
    val result: AppOpModeChangeResult,
)

data class BatchOperationReport(
    val title: String,
    val results: List<BatchOperationItemResult>,
) {
    val successCount: Int
        get() = results.count {
            it.result is AppOpModeChangeResult.Success
        }

    val failureCount: Int
        get() = results.size - successCount
}
