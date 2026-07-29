package dev.izumi.appopsnext.presentation.app_detail

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpModeChangePhase
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.appops.model.AppOpsRestorationStatus

object ModeChangeAlternativePolicy {
    fun canTryForeground(
        request: AppOpModeChangeRequest,
        result: AppOpModeChangeResult.Failure,
    ): Boolean =
        request.requestedMode == AppOpMode.ALLOW &&
            request.originalMode != AppOpMode.FOREGROUND &&
            result.phase in RejectedModePhases &&
            result.restorationStatus == AppOpsRestorationStatus.SUCCEEDED

    private val RejectedModePhases = setOf(
        AppOpModeChangePhase.APPLY_REQUESTED,
        AppOpModeChangePhase.VERIFY_REQUESTED,
    )
}
