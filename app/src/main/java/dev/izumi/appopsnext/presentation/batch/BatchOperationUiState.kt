package dev.izumi.appopsnext.presentation.batch

import dev.izumi.appopsnext.batch.model.BatchOperationReport
import dev.izumi.appopsnext.batch.model.BatchOperationTarget

data class BatchOperationRequest(
    val title: String,
    val targetCount: Int,
    val operationCount: Int,
    val targets: List<BatchOperationTarget>,
    val affectedPackages: List<String> = emptyList(),
    val previewTargets: List<BatchOperationTarget> = targets,
    /** Whether the confirmation offers to guard what the batch sets, which needs the guard to be on. */
    val offerGuard: Boolean = false,
)

sealed interface BatchOperationUiState {
    data object Idle : BatchOperationUiState

    data class Confirming(
        val request: BatchOperationRequest,
    ) : BatchOperationUiState

    data class Running(
        val request: BatchOperationRequest,
        val completed: Int,
    ) : BatchOperationUiState

    data class Finished(
        val report: BatchOperationReport,
    ) : BatchOperationUiState
}
