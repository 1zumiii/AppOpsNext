package dev.izumi.appopsnext.appops.model

import dev.izumi.appopsnext.appops.command.AppOpMode

enum class AppOpsWriteTestPhase {
    READ_ORIGINAL,
    APPLY_TEST_MODE,
    VERIFY_TEST_MODE,
    RESTORE_ORIGINAL,
    VERIFY_RESTORED,
}

enum class AppOpsRestorationStatus {
    NOT_REQUIRED,
    SUCCEEDED,
    FAILED,
}

sealed interface AppOpsWriteTestState {
    data object NotRun : AppOpsWriteTestState

    data object Running : AppOpsWriteTestState

    data class Success(
        val originalMode: AppOpMode,
        val testMode: AppOpMode,
        val restoredMode: AppOpMode,
    ) : AppOpsWriteTestState

    data class Failure(
        val phase: AppOpsWriteTestPhase,
        val originalMode: AppOpMode?,
        val restorationStatus: AppOpsRestorationStatus,
    ) : AppOpsWriteTestState
}
