package dev.izumi.appopsnext.appops.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ShellCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean,
) : Parcelable
