package dev.izumi.appops.appops

import dev.izumi.appops.appops.command.AppOpMode
import dev.izumi.appops.appops.model.AppOpIdentifier
import dev.izumi.appops.appops.model.AppOpsReadFailureReason
import dev.izumi.appops.appops.model.AppOpsReadState
import dev.izumi.appops.appops.model.AppOpsRestorationStatus
import dev.izumi.appops.appops.model.AppOpsWriteTestPhase
import dev.izumi.appops.appops.model.AppOpsWriteTestState
import dev.izumi.appops.appops.model.ShellCommandResult
import dev.izumi.appops.appops.parser.PackageOpsParser

class AppOpsRepository(
    private val privilegedGateway: PrivilegedAppOpsGateway,
    private val parser: PackageOpsParser = PackageOpsParser(),
) {
    suspend fun readPackageOps(packageName: String): AppOpsReadState {
        val commandResult = runCatching {
            privilegedGateway.getPackageOps(packageName)
        }.getOrElse {
            return AppOpsReadState.Failure(
                AppOpsReadFailureReason.BACKEND_UNAVAILABLE,
            )
        }

        if (commandResult.timedOut) {
            return AppOpsReadState.Failure(
                AppOpsReadFailureReason.COMMAND_TIMED_OUT,
            )
        }
        if (commandResult.exitCode != 0) {
            return AppOpsReadState.Failure(
                AppOpsReadFailureReason.COMMAND_FAILED,
            )
        }

        val snapshot = parser.parse(packageName, commandResult.stdout)
        return AppOpsReadState.Ready(
            operationCount = snapshot.entries.size,
        )
    }

    suspend fun runModeRoundTrip(
        packageName: String,
        operation: AppOpIdentifier,
        testMode: AppOpMode,
    ): AppOpsWriteTestState {
        val originalMode = readPackageMode(packageName, operation)
            ?: return AppOpsWriteTestState.Failure(
                phase = AppOpsWriteTestPhase.READ_ORIGINAL,
                originalMode = null,
                restorationStatus = AppOpsRestorationStatus.NOT_REQUIRED,
            )

        var primaryFailure: AppOpsWriteTestPhase? = null

        if (!setPackageMode(packageName, operation, testMode)) {
            primaryFailure = AppOpsWriteTestPhase.APPLY_TEST_MODE
        } else {
            val appliedMode = readPackageMode(packageName, operation)
            if (appliedMode != testMode) {
                primaryFailure = AppOpsWriteTestPhase.VERIFY_TEST_MODE
            }
        }

        if (!setPackageMode(packageName, operation, originalMode)) {
            return AppOpsWriteTestState.Failure(
                phase = AppOpsWriteTestPhase.RESTORE_ORIGINAL,
                originalMode = originalMode,
                restorationStatus = AppOpsRestorationStatus.FAILED,
            )
        }

        val restoredMode = readPackageMode(packageName, operation)
        if (restoredMode != originalMode) {
            return AppOpsWriteTestState.Failure(
                phase = AppOpsWriteTestPhase.VERIFY_RESTORED,
                originalMode = originalMode,
                restorationStatus = AppOpsRestorationStatus.FAILED,
            )
        }

        return if (primaryFailure == null) {
            AppOpsWriteTestState.Success(
                originalMode = originalMode,
                testMode = testMode,
                restoredMode = restoredMode,
            )
        } else {
            AppOpsWriteTestState.Failure(
                phase = primaryFailure,
                originalMode = originalMode,
                restorationStatus = AppOpsRestorationStatus.SUCCEEDED,
            )
        }
    }

    private suspend fun readPackageMode(
        packageName: String,
        operation: AppOpIdentifier,
    ): AppOpMode? {
        val result = runCatching {
            privilegedGateway.getPackageOp(packageName, operation.shellName)
        }.getOrNull() ?: return null
        if (!result.isSuccessful) return null

        val packageEntry = parser
            .parse(packageName, result.stdout)
            .entries
            .firstOrNull { entry ->
                !entry.hasUidModePrefix &&
                    entry.name.equals(operation.shellName, ignoreCase = true)
            }
            ?: return AppOpMode.DEFAULT

        return AppOpMode.fromShellValue(packageEntry.mode)
    }

    private suspend fun setPackageMode(
        packageName: String,
        operation: AppOpIdentifier,
        mode: AppOpMode,
    ): Boolean =
        runCatching {
            privilegedGateway.setPackageOpMode(
                packageName = packageName,
                operationName = operation.shellName,
                mode = mode,
            )
        }.getOrNull()?.isSuccessful == true

    private val ShellCommandResult.isSuccessful: Boolean
        get() = !timedOut && exitCode == 0
}
