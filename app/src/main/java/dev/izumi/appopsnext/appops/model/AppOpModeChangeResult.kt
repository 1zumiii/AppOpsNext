package dev.izumi.appopsnext.appops.model

import dev.izumi.appopsnext.appops.command.AppOpMode

enum class AppOpModeChangePhase {
    READ_ORIGINAL,
    CHECK_ORIGINAL,
    APPLY_REQUESTED,
    VERIFY_REQUESTED,
    RESTORE_ORIGINAL,
    VERIFY_RESTORED,
}

sealed interface AppOpModeChangeResult {
    data class Success(
        val originalMode: AppOpMode,
        val appliedMode: AppOpMode,
    ) : AppOpModeChangeResult

    data class Failure(
        val phase: AppOpModeChangePhase,
        val originalMode: AppOpMode?,
        val observedMode: AppOpMode?,
        val restorationStatus: AppOpsRestorationStatus,
    ) : AppOpModeChangeResult
}
