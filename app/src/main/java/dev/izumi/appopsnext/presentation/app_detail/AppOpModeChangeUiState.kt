package dev.izumi.appopsnext.presentation.app_detail

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.temporary.TemporaryAllowResult

data class AppOpModeChangeRequest(
    val packageName: String,
    val operationName: String,
    val scope: AppOpScope,
    val originalMode: AppOpMode,
    val requestedMode: AppOpMode,
    val affectedPackages: List<String>,
    val runtimePermissionDenied: Boolean,
    /** Null for the user this app runs in. */
    val userId: Int? = null,
    val appLabel: String = packageName,
) {
    internal val canAllowTemporarily: Boolean
        get() = userId == null && requestedMode == AppOpMode.ALLOW && !runtimePermissionDenied &&
            (scope != AppOpScope.UID || affectedPackages.size <= 1)
}

sealed interface AppOpModeChangeUiState {
    data object Idle : AppOpModeChangeUiState
    data class TemporaryAllowUnavailable(val reason: TemporaryAllowResult.Unavailable) : AppOpModeChangeUiState

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
        val temporary: Boolean = false,
    ) : AppOpModeChangeUiState
}

internal fun TemporaryAllowResult.temporaryAllowUiState(request: AppOpModeChangeRequest): AppOpModeChangeUiState =
    when (this) {
        is TemporaryAllowResult.Unavailable -> AppOpModeChangeUiState.TemporaryAllowUnavailable(this)
        is TemporaryAllowResult.Write -> when (val outcome = result) {
            is AppOpModeChangeResult.Success -> AppOpModeChangeUiState.Idle
            is AppOpModeChangeResult.Failure -> AppOpModeChangeUiState.Failure(
                request, outcome, denyFallbackAttempted = false, temporary = true,
            )
        }
    }
