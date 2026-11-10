package dev.izumi.appopsnext.appops.model

import dev.izumi.appopsnext.appops.command.AppOpMode

/** One write the system did not keep, with the step that failed, for the diagnostic log. */
data class FailedAppOpsWrite(
    val packageName: String,
    /** Null for the user this app runs in. */
    val userId: Int?,
    val operation: AppOpIdentifier,
    val scope: AppOpScope,
    val requestedMode: AppOpMode,
    val result: AppOpModeChangeResult.Failure,
)
