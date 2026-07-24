package dev.izumi.appopsnext.presentation.history

import androidx.annotation.StringRes
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.appops.model.AppOpNames
import dev.izumi.appopsnext.history.model.TrackedHistoryPermission

@StringRes
fun TrackedHistoryPermission.labelResource(): Int = when (this) {
    TrackedHistoryPermission.CAMERA -> R.string.app_op_label_camera
    TrackedHistoryPermission.MICROPHONE -> R.string.app_op_label_record_audio
    TrackedHistoryPermission.PRECISE_LOCATION ->
        R.string.app_op_label_fine_location

    TrackedHistoryPermission.APPROXIMATE_LOCATION ->
        R.string.app_op_label_coarse_location
}

fun TrackedHistoryPermission.systemOperationName(): String =
    AppOpNames.stableName(shellOperationName)
