package dev.izumi.appopsnext.nativebackend

import dev.izumi.appopsnext.appops.PrivilegedAppOpsGateway
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.command.OperationNameValidator
import dev.izumi.appopsnext.appops.command.PackageNameValidator
import dev.izumi.appopsnext.appops.model.ShellCommandResult
import dev.izumi.appopsnext.history.AppOpsHistoryOutputExtractor

internal class NativeDaemonGateway(
    private val connection: NativeDaemonConnection,
) : PrivilegedAppOpsGateway, AutoCloseable {
    val info: NativeDaemonInfo
        get() = connection.info

    override suspend fun getPackageOps(
        packageName: String,
    ): ShellCommandResult {
        requirePackageName(packageName)
        return connection.execute(
            NativeDaemonCommand(
                verb = "GET_PACKAGE_OPS",
                arguments = listOf(packageName),
            ),
        )
    }

    override suspend fun getPackageOp(
        packageName: String,
        operationName: String,
    ): ShellCommandResult {
        requirePackageName(packageName)
        requireOperationName(operationName)
        return connection.execute(
            NativeDaemonCommand(
                verb = "GET_PACKAGE_OP",
                arguments = listOf(packageName, operationName),
            ),
        )
    }

    override suspend fun getUidOps(uid: Int): ShellCommandResult {
        require(uid >= 0) { "Invalid Android UID" }
        return connection.execute(
            NativeDaemonCommand(
                verb = "GET_UID_OPS",
                arguments = listOf(uid.toString()),
            ),
        )
    }

    override suspend fun getHistory(
        operationName: String,
    ): ShellCommandResult {
        requireOperationName(operationName)
        val result = connection.execute(
            NativeDaemonCommand(
                verb = "GET_HISTORY",
                arguments = listOf(operationName),
            ),
        )
        return result.copy(
            stdout = AppOpsHistoryOutputExtractor.extract(
                commandOutput = result.stdout,
                operationName = operationName,
            ),
        )
    }

    override suspend fun setPackageOpMode(
        packageName: String,
        operationName: String,
        mode: AppOpMode,
    ): ShellCommandResult {
        requirePackageName(packageName)
        requireOperationName(operationName)
        return connection.execute(
            NativeDaemonCommand(
                verb = "SET_PACKAGE",
                arguments = listOf(
                    packageName,
                    operationName,
                    mode.shellValue,
                ),
            ),
        )
    }

    override suspend fun setUidOpMode(
        packageName: String,
        operationName: String,
        mode: AppOpMode,
    ): ShellCommandResult {
        requirePackageName(packageName)
        requireOperationName(operationName)
        return connection.execute(
            NativeDaemonCommand(
                verb = "SET_UID",
                arguments = listOf(
                    packageName,
                    operationName,
                    mode.shellValue,
                ),
            ),
        )
    }

    override fun close() {
        connection.close()
    }

    private fun requirePackageName(packageName: String) {
        require(PackageNameValidator.isValid(packageName)) {
            "Invalid Android package name"
        }
    }

    private fun requireOperationName(operationName: String) {
        require(OperationNameValidator.isValid(operationName)) {
            "Invalid AppOps operation name"
        }
    }
}
