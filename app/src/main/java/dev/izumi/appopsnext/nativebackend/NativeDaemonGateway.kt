package dev.izumi.appopsnext.nativebackend

import dev.izumi.appopsnext.appops.PrivilegedAppOpsGateway
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.command.OperationNameValidator
import dev.izumi.appopsnext.appops.command.PackageNameValidator
import dev.izumi.appopsnext.appops.command.PermissionNameValidator
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

    override suspend fun getUidStates(packageName: String): ShellCommandResult {
        requirePackageName(packageName)
        return connection.execute(
            NativeDaemonCommand(
                verb = "GET_UID_STATES",
                arguments = listOf(packageName),
            ),
        )
    }

    override suspend fun getOpStates(operationName: String): ShellCommandResult {
        requireOperationName(operationName)
        return connection.execute(
            NativeDaemonCommand("GET_OP_STATES", listOf(operationName)),
        )
    }

    override suspend fun getWatchers(): ShellCommandResult =
        connection.execute(NativeDaemonCommand(verb = "GET_WATCHERS"))

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

    override suspend fun getPackagePermissions(
        packageName: String,
    ): ShellCommandResult {
        requirePackageName(packageName)
        return connection.execute(
            NativeDaemonCommand(
                verb = "GET_PACKAGE_PERMISSIONS",
                arguments = listOf(packageName),
            ),
        )
    }

    override suspend fun setRevokedCompat(
        userId: Int,
        packageName: String,
        permissionName: String,
        revoked: Boolean,
    ): ShellCommandResult {
        require(userId >= 0) { "Invalid Android user" }
        requirePackageName(packageName)
        require(PermissionNameValidator.isValid(permissionName)) {
            "Invalid Android permission name"
        }
        return connection.execute(
            NativeDaemonCommand(
                verb = if (revoked) {
                    "SET_REVOKED_COMPAT"
                } else {
                    "CLEAR_REVOKED_COMPAT"
                },
                arguments = listOf(
                    userId.toString(),
                    packageName,
                    permissionName,
                ),
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

    override suspend fun listUsers(): ShellCommandResult =
        connection.execute(NativeDaemonCommand(verb = "LIST_USERS"))

    override suspend fun listPackagesForUser(userId: Int): ShellCommandResult {
        requireUserId(userId)
        return connection.execute(
            NativeDaemonCommand(
                verb = "LIST_PACKAGES_FOR_USER",
                arguments = listOf(userId.toString()),
            ),
        )
    }

    override suspend fun getPackageOpsForUser(
        userId: Int,
        packageName: String,
    ): ShellCommandResult {
        requireUserId(userId)
        requirePackageName(packageName)
        return connection.execute(
            NativeDaemonCommand(
                verb = "GET_USER_PACKAGE_OPS",
                arguments = listOf(userId.toString(), packageName),
            ),
        )
    }

    override suspend fun getPackageOpForUser(
        userId: Int,
        packageName: String,
        operationName: String,
    ): ShellCommandResult {
        requireUserId(userId)
        requirePackageName(packageName)
        requireOperationName(operationName)
        return connection.execute(
            NativeDaemonCommand(
                verb = "GET_USER_PACKAGE_OP",
                arguments = listOf(userId.toString(), packageName, operationName),
            ),
        )
    }

    override suspend fun setPackageOpModeForUser(
        userId: Int,
        packageName: String,
        operationName: String,
        mode: AppOpMode,
    ): ShellCommandResult = setModeForUser(
        "SET_USER_PACKAGE", userId, packageName, operationName, mode,
    )

    override suspend fun setUidOpModeForUser(
        userId: Int,
        packageName: String,
        operationName: String,
        mode: AppOpMode,
    ): ShellCommandResult = setModeForUser(
        "SET_USER_UID", userId, packageName, operationName, mode,
    )

    private suspend fun setModeForUser(
        verb: String,
        userId: Int,
        packageName: String,
        operationName: String,
        mode: AppOpMode,
    ): ShellCommandResult {
        requireUserId(userId)
        requirePackageName(packageName)
        requireOperationName(operationName)
        return connection.execute(
            NativeDaemonCommand(
                verb = verb,
                arguments = listOf(
                    userId.toString(),
                    packageName,
                    operationName,
                    mode.shellValue,
                ),
            ),
        )
    }

    private fun requireUserId(userId: Int) {
        require(userId >= 0) { "Invalid Android user" }
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
