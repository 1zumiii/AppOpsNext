package dev.izumi.appopsnext.appops

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.ShellCommandResult

interface PrivilegedAppOpsGateway {
    suspend fun getPackageOps(packageName: String): ShellCommandResult

    suspend fun getPackageOp(
        packageName: String,
        operationName: String,
    ): ShellCommandResult

    suspend fun getUidOps(uid: Int): ShellCommandResult =
        error("UID-scoped reads are not implemented by this gateway")

    suspend fun setPackageOpMode(
        packageName: String,
        operationName: String,
        mode: AppOpMode,
    ): ShellCommandResult

    suspend fun setUidOpMode(
        packageName: String,
        operationName: String,
        mode: AppOpMode,
    ): ShellCommandResult
}
