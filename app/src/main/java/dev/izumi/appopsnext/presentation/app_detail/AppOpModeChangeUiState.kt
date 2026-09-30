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
    val runtimePermissionDenied: Boolean,
)

sealed interface AppOpModeChangeUiState {
    data object Idle : AppOpModeChangeUiState

    data class Confirming(
        val request: AppOpModeChangeRequest,
    ) : AppOpModeChangeUiState

    data class Applying(
        val request: AppOpModeChangeRequest,
    ) : AppOpModeChangeUiState

    data class DenyFallbackApplied(
        val request: AppOpModeChangeRequest,
    ) : AppOpModeChangeUiState

    /** Ignore was reached through the permission's compatibility flag. */
    data class PermissionFlagApplied(
        val request: AppOpModeChangeRequest,
        val denyFallbackAttempted: Boolean,
    ) : AppOpModeChangeUiState

    data class Failure(
        val request: AppOpModeChangeRequest,
        val result: AppOpModeChangeResult.Failure,
        val denyFallbackAttempted: Boolean,
        /** Android keeps this op in line with the runtime permission. */
        val runtimePermissionManaged: Boolean = false,
    ) : AppOpModeChangeUiState
}
