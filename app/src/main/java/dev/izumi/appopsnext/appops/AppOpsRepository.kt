package dev.izumi.appopsnext.appops

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpIdentifier
import dev.izumi.appopsnext.appops.model.AppOpModeChangePhase
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.appops.model.AppOpsReadFailureReason
import dev.izumi.appopsnext.appops.model.AppOpsReadState
import dev.izumi.appopsnext.appops.model.AppOpsRestorationStatus
import dev.izumi.appopsnext.appops.model.AppOpsWriteTestPhase
import dev.izumi.appopsnext.appops.model.AppOpsWriteTestState
import dev.izumi.appopsnext.appops.model.PackageOpsLoadResult
import dev.izumi.appopsnext.appops.model.ShellCommandResult
import dev.izumi.appopsnext.appops.parser.PackageOpsParser

class AppOpsRepository(
    private val privilegedGateway: PrivilegedAppOpsGateway,
    private val parser: PackageOpsParser = PackageOpsParser(),
) {
    suspend fun readPackageOps(packageName: String): AppOpsReadState {
        return when (val result = loadPackageOps(packageName)) {
            is PackageOpsLoadResult.Success -> AppOpsReadState.Ready(
                operationCount = result.snapshot.entries.size,
            )

            is PackageOpsLoadResult.Failure -> AppOpsReadState.Failure(
                result.reason,
            )
        }
    }

    suspend fun loadPackageOps(packageName: String): PackageOpsLoadResult {
        val commandResult = runCatching {
            privilegedGateway.getPackageOps(packageName)
        }.getOrElse {
            return PackageOpsLoadResult.Failure(
                AppOpsReadFailureReason.BACKEND_UNAVAILABLE,
            )
        }

        if (commandResult.timedOut) {
            return PackageOpsLoadResult.Failure(
                AppOpsReadFailureReason.COMMAND_TIMED_OUT,
            )
        }
        if (commandResult.exitCode != 0) {
            return PackageOpsLoadResult.Failure(
                AppOpsReadFailureReason.COMMAND_FAILED,
            )
        }

        val snapshot = parser.parse(packageName, commandResult.stdout)
        return PackageOpsLoadResult.Success(
            snapshot = snapshot,
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

    suspend fun changePackageMode(
        packageName: String,
        operation: AppOpIdentifier,
        expectedOriginalMode: AppOpMode,
        requestedMode: AppOpMode,
    ): AppOpModeChangeResult {
        val originalMode = readPackageMode(packageName, operation)
            ?: return AppOpModeChangeResult.Failure(
                phase = AppOpModeChangePhase.READ_ORIGINAL,
                originalMode = null,
                observedMode = null,
                restorationStatus = AppOpsRestorationStatus.NOT_REQUIRED,
            )

        if (originalMode != expectedOriginalMode) {
            return AppOpModeChangeResult.Failure(
                phase = AppOpModeChangePhase.CHECK_ORIGINAL,
                originalMode = originalMode,
                observedMode = originalMode,
                restorationStatus = AppOpsRestorationStatus.NOT_REQUIRED,
            )
        }

        if (originalMode == requestedMode) {
            return AppOpModeChangeResult.Success(
                originalMode = originalMode,
                appliedMode = requestedMode,
            )
        }

        if (!setPackageMode(packageName, operation, requestedMode)) {
            return restoreAfterFailedChange(
                packageName = packageName,
                operation = operation,
                originalMode = originalMode,
                primaryFailure = AppOpModeChangePhase.APPLY_REQUESTED,
                observedMode = null,
            )
        }

        val observedMode = readPackageMode(packageName, operation)
        if (observedMode != requestedMode) {
            return restoreAfterFailedChange(
                packageName = packageName,
                operation = operation,
                originalMode = originalMode,
                primaryFailure = AppOpModeChangePhase.VERIFY_REQUESTED,
                observedMode = observedMode,
            )
        }

        return AppOpModeChangeResult.Success(
            originalMode = originalMode,
            appliedMode = observedMode,
        )
    }

    private suspend fun restoreAfterFailedChange(
        packageName: String,
        operation: AppOpIdentifier,
        originalMode: AppOpMode,
        primaryFailure: AppOpModeChangePhase,
        observedMode: AppOpMode?,
    ): AppOpModeChangeResult {
        if (!setPackageMode(packageName, operation, originalMode)) {
            return AppOpModeChangeResult.Failure(
                phase = AppOpModeChangePhase.RESTORE_ORIGINAL,
                originalMode = originalMode,
                observedMode = observedMode,
                restorationStatus = AppOpsRestorationStatus.FAILED,
            )
        }

        val restoredMode = readPackageMode(packageName, operation)
        if (restoredMode != originalMode) {
            return AppOpModeChangeResult.Failure(
                phase = AppOpModeChangePhase.VERIFY_RESTORED,
                originalMode = originalMode,
                observedMode = restoredMode,
                restorationStatus = AppOpsRestorationStatus.FAILED,
            )
        }

        return AppOpModeChangeResult.Failure(
            phase = primaryFailure,
            originalMode = originalMode,
            observedMode = observedMode,
            restorationStatus = AppOpsRestorationStatus.SUCCEEDED,
        )
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
