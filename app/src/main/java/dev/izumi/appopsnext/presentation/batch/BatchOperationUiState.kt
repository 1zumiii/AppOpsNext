package dev.izumi.appopsnext.presentation.batch

import dev.izumi.appopsnext.batch.model.BatchOperationReport
import dev.izumi.appopsnext.batch.model.BatchOperationTarget

data class BatchOperationRequest(
    val title: String,
    val targetCount: Int,
    val operationCount: Int,
    val targets: List<BatchOperationTarget>,
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
