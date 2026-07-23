package dev.izumi.appops.appops

import dev.izumi.appops.appops.command.AppOpMode
import dev.izumi.appops.appops.model.ShellCommandResult

interface PrivilegedAppOpsGateway {
    suspend fun getPackageOps(packageName: String): ShellCommandResult

    suspend fun getPackageOp(
        packageName: String,
        operationName: String,
    ): ShellCommandResult

    suspend fun setPackageOpMode(
        packageName: String,
        operationName: String,
        mode: AppOpMode,
    ): ShellCommandResult
}
