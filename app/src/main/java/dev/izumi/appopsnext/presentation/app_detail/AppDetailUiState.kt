package dev.izumi.appopsnext.presentation.app_detail

import dev.izumi.appopsnext.appops.model.AppOpsReadFailureReason
import dev.izumi.appopsnext.appops.model.PackageOpsSnapshot
import dev.izumi.appopsnext.apps.model.InstalledApp

sealed interface AppDetailUiState {
    data object Idle : AppDetailUiState

    data class WaitingForBackend(
        val app: InstalledApp,
    ) : AppDetailUiState

    data class Loading(
        val app: InstalledApp,
    ) : AppDetailUiState

    data class Ready(
        val app: InstalledApp,
        val snapshot: PackageOpsSnapshot,
    ) : AppDetailUiState

    data class Failure(
        val app: InstalledApp,
        val reason: AppOpsReadFailureReason,
    ) : AppDetailUiState
}
