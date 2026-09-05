package dev.izumi.appopsnext.appops.model

/** Cleanup evidence retained even when the requesting screen has gone away. */
data class CancelledAppOpsWrite(
    val packageName: String,
    val operation: AppOpIdentifier,
    val scope: AppOpScope,
    val result: AppOpModeChangeResult.Failure,
)
