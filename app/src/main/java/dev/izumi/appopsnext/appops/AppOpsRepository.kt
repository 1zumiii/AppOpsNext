package dev.izumi.appopsnext.appops

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpIdentifier
import dev.izumi.appopsnext.appops.model.AppOpModeChangePhase
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.appops.model.AppOpsReadFailureReason
import dev.izumi.appopsnext.appops.model.AppOpsReadState
import dev.izumi.appopsnext.appops.model.AppOpsRestorationStatus
import dev.izumi.appopsnext.appops.model.AppOpsWriteTestPhase
import dev.izumi.appopsnext.appops.model.AppOpsWriteTestState
import dev.izumi.appopsnext.appops.model.PackageOpsLoadResult
import dev.izumi.appopsnext.appops.model.PackageOpsSnapshot
import dev.izumi.appopsnext.appops.model.ShellCommandResult
import dev.izumi.appopsnext.appops.parser.PackageOpsParser
import java.util.Locale

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

        val parsedSnapshot = parser.parse(packageName, commandResult.stdout)
        val snapshot = if (
            parsedSnapshot.entries.any { it.scope == AppOpScope.UID }
        ) {
            resolveEntryScopes(parsedSnapshot)
        } else {
            parsedSnapshot
        }
        return PackageOpsLoadResult.Success(
            snapshot = snapshot,
        )
    }

    private suspend fun resolveEntryScopes(
        snapshot: PackageOpsSnapshot,
    ): PackageOpsSnapshot {
        val resolvedEntries = snapshot.entries
            .distinctBy { it.name.lowercase(Locale.ROOT) }
            .flatMap { discoveredEntry ->
                val result = runCatching {
                    privilegedGateway.getPackageOp(
                        packageName = snapshot.packageName,
                        operationName = discoveredEntry.name,
                    )
                }.getOrNull()

                if (result?.isSuccessful == true) {
                    parser
                        .parse(snapshot.packageName, result.stdout)
                        .entries
                        .ifEmpty {
                            snapshot.entries.filter {
                                it.name.equals(
                                    discoveredEntry.name,
                                    ignoreCase = true,
                                )
                            }
                        }
                } else {
                    snapshot.entries.filter {
                        it.name.equals(
                            discoveredEntry.name,
                            ignoreCase = true,
                        )
                    }
                }
            }

        return snapshot.copy(entries = resolvedEntries)
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
        return changeMode(
            packageName = packageName,
            operation = operation,
            scope = AppOpScope.PACKAGE,
            expectedOriginalMode = expectedOriginalMode,
            requestedMode = requestedMode,
        )
    }

    suspend fun changeMode(
        packageName: String,
        operation: AppOpIdentifier,
        scope: AppOpScope,
        expectedOriginalMode: AppOpMode,
        requestedMode: AppOpMode,
    ): AppOpModeChangeResult {
        val originalMode = readMode(packageName, operation, scope)
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

        if (!setMode(packageName, operation, scope, requestedMode)) {
            return restoreAfterFailedChange(
                packageName = packageName,
                operation = operation,
                scope = scope,
                originalMode = originalMode,
                primaryFailure = AppOpModeChangePhase.APPLY_REQUESTED,
                observedMode = null,
            )
        }

        val observedMode = readMode(packageName, operation, scope)
        if (observedMode != requestedMode) {
            return restoreAfterFailedChange(
                packageName = packageName,
                operation = operation,
                scope = scope,
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
        scope: AppOpScope,
        originalMode: AppOpMode,
        primaryFailure: AppOpModeChangePhase,
        observedMode: AppOpMode?,
    ): AppOpModeChangeResult {
        if (!setMode(packageName, operation, scope, originalMode)) {
            return AppOpModeChangeResult.Failure(
                phase = AppOpModeChangePhase.RESTORE_ORIGINAL,
                originalMode = originalMode,
                observedMode = observedMode,
                restorationStatus = AppOpsRestorationStatus.FAILED,
            )
        }

        val restoredMode = readMode(packageName, operation, scope)
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
    ): AppOpMode? =
        readMode(packageName, operation, AppOpScope.PACKAGE)

    private suspend fun readMode(
        packageName: String,
        operation: AppOpIdentifier,
        scope: AppOpScope,
    ): AppOpMode? {
        val result = runCatching {
            privilegedGateway.getPackageOp(packageName, operation.shellName)
        }.getOrNull() ?: return null
        if (!result.isSuccessful) return null

        val scopedEntry = parser
            .parse(packageName, result.stdout)
            .entries
            .firstOrNull { entry ->
                entry.scope == scope &&
                    entry.name.equals(operation.shellName, ignoreCase = true)
            }
            ?: return AppOpMode.DEFAULT

        return AppOpMode.fromShellValue(scopedEntry.mode)
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

    private suspend fun setMode(
        packageName: String,
        operation: AppOpIdentifier,
        scope: AppOpScope,
        mode: AppOpMode,
    ): Boolean =
        when (scope) {
            AppOpScope.PACKAGE ->
                setPackageMode(packageName, operation, mode)

            AppOpScope.UID ->
                runCatching {
                    privilegedGateway.setUidOpMode(
                        packageName = packageName,
                        operationName = operation.shellName,
                        mode = mode,
                    )
                }.getOrNull()?.isSuccessful == true
        }

    private val ShellCommandResult.isSuccessful: Boolean
        get() = !timedOut && exitCode == 0
}
