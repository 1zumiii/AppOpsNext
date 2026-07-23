package dev.izumi.appopsnext.presentation.app_detail

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.appops.model.AppOpScope

data class AppOpModeChangeRequest(
    val packageName: String,
    val operationName: String,
    val scope: AppOpScope,
    val originalMode: AppOpMode,
    val requestedMode: AppOpMode,
    val affectedPackages: List<String>,
)

sealed interface AppOpModeChangeUiState {
    data object Idle : AppOpModeChangeUiState

    data class Confirming(
        val request: AppOpModeChangeRequest,
    ) : AppOpModeChangeUiState

    data class Applying(
        val request: AppOpModeChangeRequest,
    ) : AppOpModeChangeUiState

    data class Failure(
        val request: AppOpModeChangeRequest,
        val result: AppOpModeChangeResult.Failure,
    ) : AppOpModeChangeUiState
}
