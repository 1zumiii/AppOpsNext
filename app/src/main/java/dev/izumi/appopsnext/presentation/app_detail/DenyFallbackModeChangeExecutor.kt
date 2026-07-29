package dev.izumi.appopsnext.presentation.app_detail

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpModeChangePhase
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.appops.model.AppOpsRestorationStatus

data class SingleModeChangeOutcome(
    val result: AppOpModeChangeResult,
    val denyFallbackAttempted: Boolean,
)

class DenyFallbackModeChangeExecutor(
    private val applyMode:
        suspend (AppOpMode) -> AppOpModeChangeResult,
) {
    suspend fun execute(
        requestedMode: AppOpMode,
    ): SingleModeChangeOutcome {
        val initialResult = applyMode(requestedMode)
        if (
            requestedMode != AppOpMode.DENY ||
            !initialResult.isSafeDenyRejection()
        ) {
            return SingleModeChangeOutcome(
                result = initialResult,
                denyFallbackAttempted = false,
            )
        }

        return SingleModeChangeOutcome(
            result = applyMode(AppOpMode.IGNORE),
            denyFallbackAttempted = true,
        )
    }

    private fun AppOpModeChangeResult.isSafeDenyRejection(): Boolean =
        this is AppOpModeChangeResult.Failure &&
            phase in DenyRejectionPhases &&
            restorationStatus == AppOpsRestorationStatus.SUCCEEDED

    private companion object {
        val DenyRejectionPhases = setOf(
            AppOpModeChangePhase.APPLY_REQUESTED,
            AppOpModeChangePhase.VERIFY_REQUESTED,
        )
    }
}
