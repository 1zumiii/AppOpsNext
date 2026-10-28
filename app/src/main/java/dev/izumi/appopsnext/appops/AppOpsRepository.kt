package dev.izumi.appopsnext.appops

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpIdentifier
import dev.izumi.appopsnext.appops.model.AppOpModeChangePhase
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.appops.model.AppOpsReadFailureReason
import dev.izumi.appopsnext.appops.model.AppOpsReadState
import dev.izumi.appopsnext.appops.model.AppOpsRestorationStatus
import dev.izumi.appopsnext.appops.model.PackageOpsLoadResult
import dev.izumi.appopsnext.appops.model.PackageOpsSnapshot
import dev.izumi.appopsnext.appops.model.ShellCommandResult
import dev.izumi.appopsnext.appops.parser.AppOpsDump
import dev.izumi.appopsnext.appops.parser.OpStates
import dev.izumi.appopsnext.appops.parser.OpStatesParser
import dev.izumi.appopsnext.appops.parser.PackageOpsParser
import dev.izumi.appopsnext.appops.parser.RuntimePermissionState
import dev.izumi.appopsnext.appops.parser.RuntimePermissionStateParser
import dev.izumi.appopsnext.appops.model.CancelledAppOpsWrite
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppOpsRepository(
    private val privilegedGateway: PrivilegedAppOpsGateway,
    private val parser: PackageOpsParser = PackageOpsParser(),
    private val permissionParser: RuntimePermissionStateParser =
        RuntimePermissionStateParser(),
    private val writeCoordinator: AppOpsWriteCoordinator = AppOpsWriteCoordinator.Shared,
    private val cancellationRecoveryTimeoutMillis: Long = 25_000L,
    private val onCancelledWrite: (CancelledAppOpsWrite) -> Unit = {},
    private val structuredReads: StructuredAppOpsReads? = null,
) {
    suspend fun readPackageOps(
        packageName: String,
        uid: Int? = null,
        userId: Int? = null,
    ): AppOpsReadState {
        return when (val result = loadPackageOps(packageName, uid, userId)) {
            is PackageOpsLoadResult.Success -> AppOpsReadState.Ready(
                operationCount = result.snapshot.entries.size,
            )

            is PackageOpsLoadResult.Failure -> AppOpsReadState.Failure(
                result.reason,
            )
        }
    }

    /**
     * [userId] null means the user this app runs in. For another user pass the
     * app's [uid] when it is known, since the binder read is uid based.
     */
    suspend fun loadPackageOps(
        packageName: String,
        uid: Int? = null,
        userId: Int? = null,
    ): PackageOpsLoadResult {
        structuredReads?.packageOps(packageName, uid, userId)?.let { snapshot ->
            return PackageOpsLoadResult.Success(snapshot)
        }
        return loadPackageOpsFromText(packageName, uid, userId)
    }

    /** The command path, also the reference the read probe compares against. */
    suspend fun loadPackageOpsFromText(
        packageName: String,
        uid: Int? = null,
        userId: Int? = null,
    ): PackageOpsLoadResult {
        val commandResult = catchBackendFailure {
            if (userId == null) {
                privilegedGateway.getPackageOps(packageName)
            } else {
                privilegedGateway.getPackageOpsForUser(userId, packageName)
            }
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
            resolveEntryScopes(parsedSnapshot, uid, userId)
        } else {
            parsedSnapshot
        }
        return PackageOpsLoadResult.Success(
            snapshot = snapshot,
        )
    }

    /**
     * Every UID and package mode of one operation for [userId]. [uids] are the
     * apps on screen, which the binder path needs for UID modes.
     *
     * @throws IllegalStateException when neither path can read the operation.
     */
    suspend fun loadOpStates(
        operationName: String,
        userId: Int,
        uids: Collection<Int>,
    ): OpStates =
        structuredReads?.opStates(operationName, userId, uids)
            ?: loadOpStatesFromText(operationName, userId)

    /** @throws IllegalStateException when the dump fails or is not one we recognize. */
    suspend fun loadOpStatesFromText(
        operationName: String,
        userId: Int,
    ): OpStates {
        val result = privilegedGateway.getOpStates(operationName)
        check(result.isSuccessful) { "AppOps query failed" }
        check(result.stdout.lineSequence().any(AppOpsDump::isHeader)) {
            val firstLine = result.stdout.lineSequence().firstOrNull(String::isNotBlank)
            "Unexpected AppOps dump, first line: ${firstLine?.trim()?.take(120)}"
        }
        return OpStatesParser.parse(
            output = result.stdout,
            operationName = operationName,
            userId = userId,
        )
    }

    private suspend fun resolveEntryScopes(
        snapshot: PackageOpsSnapshot,
        uid: Int?,
        userId: Int?,
    ): PackageOpsSnapshot {
        val uidResolvedSnapshot = uid?.let {
            resolveEntryScopesUsingUidSnapshot(snapshot, it)
        }
        return uidResolvedSnapshot ?: resolveEntryScopesIndividually(snapshot, userId)
    }

    private suspend fun resolveEntryScopesUsingUidSnapshot(
        snapshot: PackageOpsSnapshot,
        uid: Int,
    ): PackageOpsSnapshot? {
        val result = catchBackendFailure {
            privilegedGateway.getUidOps(uid)
        }.getOrNull()
        if (result?.isSuccessful != true) return null

        val uidEntries = parser
            .parse(snapshot.packageName, result.stdout)
            .entries
            .map { entry -> entry.copy(hasUidModePrefix = true) }
        if (uidEntries.isEmpty()) return null

        val fullEntries = snapshot.entries
        val hasMatchingUidPrefix =
            fullEntries.size >= uidEntries.size &&
                fullEntries
                    .take(uidEntries.size)
                    .zip(uidEntries)
                    .all { (fullEntry, uidEntry) ->
                        fullEntry.name.equals(
                            uidEntry.name,
                            ignoreCase = true,
                        ) && fullEntry.mode.equals(
                            uidEntry.mode,
                            ignoreCase = true,
                        )
                    }
        if (!hasMatchingUidPrefix) return null

        return snapshot.copy(
            entries = uidEntries + fullEntries.drop(uidEntries.size),
        )
    }

    private suspend fun resolveEntryScopesIndividually(
        snapshot: PackageOpsSnapshot,
        userId: Int?,
    ): PackageOpsSnapshot {
        val resolvedEntries = snapshot.entries
            .distinctBy { it.name.lowercase(Locale.ROOT) }
            .flatMap { discoveredEntry ->
                val result = catchBackendFailure {
                    if (userId == null) {
                        privilegedGateway.getPackageOp(
                            packageName = snapshot.packageName,
                            operationName = discoveredEntry.name,
                        )
                    } else {
                        privilegedGateway.getPackageOpForUser(
                            userId = userId,
                            packageName = snapshot.packageName,
                            operationName = discoveredEntry.name,
                        )
                    }
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

    suspend fun changePackageMode(
        packageName: String,
        operation: AppOpIdentifier,
        expectedOriginalMode: AppOpMode,
        requestedMode: AppOpMode,
        userId: Int? = null,
    ): AppOpModeChangeResult {
        return changeMode(
            packageName = packageName,
            operation = operation,
            scope = AppOpScope.PACKAGE,
            expectedOriginalMode = expectedOriginalMode,
            requestedMode = requestedMode,
            userId = userId,
        )
    }

    /**
     * Keep retries in one transaction. Inside this block use the supplied writer,
     * not the repository's standalone write methods (which acquire the queue).
     */
    suspend fun <T> withWriteTransaction(
        action: suspend (WriteTransaction) -> T,
    ): T = writeCoordinator.serialize {
        val transaction = WriteTransaction()
        try {
            action(transaction)
        } finally {
            transaction.finish()
        }
    }

    inner class WriteTransaction internal constructor() {
        private val operationMutex = Mutex()
        private var active = true

        internal fun finish() {
            active = false
        }

        suspend fun changeMode(
            packageName: String,
            operation: AppOpIdentifier,
            scope: AppOpScope,
            expectedOriginalMode: AppOpMode,
            requestedMode: AppOpMode,
            userId: Int? = null,
        ): AppOpModeChangeResult = operationMutex.withLock {
            check(active) { "Write transaction has finished" }
            changeModeInTransaction(
                packageName, operation, scope, expectedOriginalMode, requestedMode,
                userId,
            )
        }

        suspend fun applyMode(
            packageName: String,
            operation: AppOpIdentifier,
            scope: AppOpScope,
            requestedMode: AppOpMode,
            userId: Int? = null,
        ): AppOpModeChangeResult = operationMutex.withLock {
            check(active) { "Write transaction has finished" }
            applyModeInTransaction(packageName, operation, scope, requestedMode, userId)
        }

        /**
         * Changes the UID-level result through the permission's
         * `REVOKED_COMPAT` flag. Returns null when the flag cannot produce the
         * requested mode, so the caller keeps its earlier result.
         */
        suspend fun applyRevokedCompat(
            packageName: String,
            uid: Int,
            operation: AppOpIdentifier,
            permissionName: String,
            requestedMode: AppOpMode,
            userId: Int? = null,
        ): AppOpModeChangeResult? = operationMutex.withLock {
            check(active) { "Write transaction has finished" }
            applyRevokedCompatInTransaction(
                packageName, uid, operation, permissionName, requestedMode, userId,
            )
        }
    }

    suspend fun changeMode(
        packageName: String,
        operation: AppOpIdentifier,
        scope: AppOpScope,
        expectedOriginalMode: AppOpMode,
        requestedMode: AppOpMode,
        userId: Int? = null,
    ): AppOpModeChangeResult = withWriteTransaction { transaction ->
        transaction.changeMode(
            packageName, operation, scope, expectedOriginalMode, requestedMode,
            userId,
        )
    }

    suspend fun applyMode(
        packageName: String,
        operation: AppOpIdentifier,
        scope: AppOpScope,
        requestedMode: AppOpMode,
        userId: Int? = null,
    ): AppOpModeChangeResult = withWriteTransaction { transaction ->
        transaction.applyMode(packageName, operation, scope, requestedMode, userId)
    }

    private suspend fun changeModeInTransaction(
        packageName: String,
        operation: AppOpIdentifier,
        scope: AppOpScope,
        expectedOriginalMode: AppOpMode,
        requestedMode: AppOpMode,
        userId: Int?,
    ): AppOpModeChangeResult {
        val originalMode = readMode(packageName, operation, scope, userId)
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

        return applyRequestedMode(
            packageName = packageName,
            operation = operation,
            scope = scope,
            originalMode = originalMode,
            requestedMode = requestedMode,
            userId = userId,
        )
    }

    private suspend fun applyModeInTransaction(
        packageName: String,
        operation: AppOpIdentifier,
        scope: AppOpScope,
        requestedMode: AppOpMode,
        userId: Int?,
    ): AppOpModeChangeResult {
        val originalMode = readMode(packageName, operation, scope, userId)
            ?: return AppOpModeChangeResult.Failure(
                phase = AppOpModeChangePhase.READ_ORIGINAL,
                originalMode = null,
                observedMode = null,
                restorationStatus = AppOpsRestorationStatus.NOT_REQUIRED,
            )
        return applyRequestedMode(
            packageName = packageName,
            operation = operation,
            scope = scope,
            originalMode = originalMode,
            requestedMode = requestedMode,
            userId = userId,
        )
    }

    private suspend fun applyRequestedMode(
        packageName: String,
        operation: AppOpIdentifier,
        scope: AppOpScope,
        originalMode: AppOpMode,
        requestedMode: AppOpMode,
        userId: Int?,
    ): AppOpModeChangeResult {
        if (originalMode == requestedMode) {
            return AppOpModeChangeResult.Success(
                originalMode = originalMode,
                appliedMode = requestedMode,
            )
        }

        // Before the first possible side effect, cancellation needs no rollback.
        currentCoroutineContext().ensureActive()
        var failurePhase = AppOpModeChangePhase.APPLY_REQUESTED
        try {
            if (!setMode(packageName, operation, scope, requestedMode, userId)) {
                return restoreAfterFailedChange(
                    packageName = packageName,
                    operation = operation,
                    scope = scope,
                    originalMode = originalMode,
                    primaryFailure = AppOpModeChangePhase.APPLY_REQUESTED,
                    observedMode = null,
                    userId = userId,
                )
            }

            failurePhase = AppOpModeChangePhase.VERIFY_REQUESTED
            val observedMode = readMode(packageName, operation, scope, userId)
            if (observedMode != requestedMode) {
                return restoreAfterFailedChange(
                    packageName = packageName,
                    operation = operation,
                    scope = scope,
                    originalMode = originalMode,
                    primaryFailure = AppOpModeChangePhase.VERIFY_REQUESTED,
                    observedMode = observedMode,
                    userId = userId,
                )
            }

            return AppOpModeChangeResult.Success(
                originalMode = originalMode,
                appliedMode = observedMode,
            )
        } catch (cancelled: CancellationException) {
            // Keep the shared write queue until cleanup finishes. A cancelled
            // caller must not cancel restoration or let another writer overtake it.
            val recovery = withContext(NonCancellable) {
                withTimeoutOrNull(cancellationRecoveryTimeoutMillis) {
                    restoreAfterFailedChange(
                        packageName = packageName,
                        operation = operation,
                        scope = scope,
                        originalMode = originalMode,
                        primaryFailure = failurePhase,
                        observedMode = null,
                        userId = userId,
                    )
                } ?: AppOpModeChangeResult.Failure(
                    phase = AppOpModeChangePhase.RESTORE_ORIGINAL,
                    originalMode = originalMode,
                    observedMode = null,
                    restorationStatus = AppOpsRestorationStatus.FAILED,
                )
            }
            onCancelledWrite(
                CancelledAppOpsWrite(packageName, operation, scope, recovery),
            )
            throw cancelled
        }
    }

    private suspend fun restoreAfterFailedChange(
        packageName: String,
        operation: AppOpIdentifier,
        scope: AppOpScope,
        originalMode: AppOpMode,
        primaryFailure: AppOpModeChangePhase,
        observedMode: AppOpMode?,
        userId: Int?,
    ): AppOpModeChangeResult.Failure {
        if (!setMode(packageName, operation, scope, originalMode, userId)) {
            return AppOpModeChangeResult.Failure(
                phase = AppOpModeChangePhase.RESTORE_ORIGINAL,
                originalMode = originalMode,
                observedMode = observedMode,
                restorationStatus = AppOpsRestorationStatus.FAILED,
            )
        }

        val restoredMode = readMode(packageName, operation, scope, userId)
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

    private suspend fun applyRevokedCompatInTransaction(
        packageName: String,
        uid: Int,
        operation: AppOpIdentifier,
        permissionName: String,
        requestedMode: AppOpMode,
        requestedUserId: Int?,
    ): AppOpModeChangeResult? {
        val revoke = when (requestedMode) {
            AppOpMode.IGNORE -> true
            AppOpMode.ALLOW, AppOpMode.FOREGROUND -> false
            AppOpMode.DENY, AppOpMode.DEFAULT -> return null
        }
        val userId = uid / PER_USER_UID_RANGE
        val original = readPermissionState(packageName, userId, permissionName)
            ?: return null
        // The flag only restricts a granted permission. It cannot grant one.
        if (!original.granted || original.revokedCompat == revoke) return null
        val originalMode = readMode(
            packageName, operation, AppOpScope.UID, requestedUserId,
        ) ?: return null

        currentCoroutineContext().ensureActive()
        var failurePhase = AppOpModeChangePhase.APPLY_REQUESTED
        try {
            if (!setRevokedCompat(userId, packageName, permissionName, revoke)) {
                return restoreRevokedCompat(
                    packageName, userId, operation, permissionName,
                    original, originalMode,
                    primaryFailure = AppOpModeChangePhase.APPLY_REQUESTED,
                    observedMode = null,
                    restoreUserId = requestedUserId,
                )
            }

            failurePhase = AppOpModeChangePhase.VERIFY_REQUESTED
            val observedMode = readMode(
                packageName, operation, AppOpScope.UID, requestedUserId,
            )
            // Clearing the flag hands the mode back to Android, which derives
            // allow or foreground from the grant. Either lifts the restriction.
            val verified = if (revoke) {
                observedMode == AppOpMode.IGNORE
            } else {
                observedMode != null &&
                    observedMode != AppOpMode.IGNORE &&
                    observedMode != AppOpMode.DENY
            }
            if (!verified || observedMode == null) {
                return restoreRevokedCompat(
                    packageName, userId, operation, permissionName,
                    original, originalMode,
                    primaryFailure = AppOpModeChangePhase.VERIFY_REQUESTED,
                    observedMode = observedMode,
                    restoreUserId = requestedUserId,
                )
            }

            return AppOpModeChangeResult.Success(
                originalMode = originalMode,
                appliedMode = observedMode,
            )
        } catch (cancelled: CancellationException) {
            val recovery = withContext(NonCancellable) {
                withTimeoutOrNull(cancellationRecoveryTimeoutMillis) {
                    restoreRevokedCompat(
                        packageName, userId, operation, permissionName,
                        original, originalMode,
                        primaryFailure = failurePhase,
                        observedMode = null,
                        restoreUserId = requestedUserId,
                    )
                } ?: AppOpModeChangeResult.Failure(
                    phase = AppOpModeChangePhase.RESTORE_ORIGINAL,
                    originalMode = originalMode,
                    observedMode = null,
                    restorationStatus = AppOpsRestorationStatus.FAILED,
                )
            }
            onCancelledWrite(
                CancelledAppOpsWrite(
                    packageName, operation, AppOpScope.UID, recovery,
                ),
            )
            throw cancelled
        }
    }

    private suspend fun restoreRevokedCompat(
        packageName: String,
        userId: Int,
        operation: AppOpIdentifier,
        permissionName: String,
        original: RuntimePermissionState,
        originalMode: AppOpMode,
        primaryFailure: AppOpModeChangePhase,
        observedMode: AppOpMode?,
        restoreUserId: Int?,
    ): AppOpModeChangeResult.Failure {
        if (
            !setRevokedCompat(
                userId, packageName, permissionName, original.revokedCompat,
            )
        ) {
            return AppOpModeChangeResult.Failure(
                phase = AppOpModeChangePhase.RESTORE_ORIGINAL,
                originalMode = originalMode,
                observedMode = observedMode,
                restorationStatus = AppOpsRestorationStatus.FAILED,
            )
        }

        val restored = readPermissionState(packageName, userId, permissionName)
        val restoredMode = readMode(
            packageName, operation, AppOpScope.UID, restoreUserId,
        )
        if (restored != original || restoredMode != originalMode) {
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

    private suspend fun readPermissionState(
        packageName: String,
        userId: Int,
        permissionName: String,
    ): RuntimePermissionState? {
        val result = catchBackendFailure {
            privilegedGateway.getPackagePermissions(packageName)
        }.getOrNull() ?: return null
        if (!result.isSuccessful) return null
        return permissionParser.parse(result.stdout, userId, permissionName)
    }

    private suspend fun setRevokedCompat(
        userId: Int,
        packageName: String,
        permissionName: String,
        revoked: Boolean,
    ): Boolean =
        catchBackendFailure {
            privilegedGateway.setRevokedCompat(
                userId = userId,
                packageName = packageName,
                permissionName = permissionName,
                revoked = revoked,
            )
        }.getOrNull()?.isSuccessful == true

    suspend fun readMode(
        packageName: String,
        operation: AppOpIdentifier,
        scope: AppOpScope,
        userId: Int? = null,
    ): AppOpMode? {
        structuredReads?.mode(packageName, operation.shellName, scope, userId)
            ?.let { return it }
        val result = catchBackendFailure {
            if (userId == null) {
                privilegedGateway.getPackageOp(packageName, operation.shellName)
            } else {
                privilegedGateway.getPackageOpForUser(
                    userId, packageName, operation.shellName,
                )
            }
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

    private suspend fun setMode(
        packageName: String,
        operation: AppOpIdentifier,
        scope: AppOpScope,
        mode: AppOpMode,
        userId: Int?,
    ): Boolean =
        catchBackendFailure {
            val name = operation.shellName
            when {
                userId == null && scope == AppOpScope.PACKAGE ->
                    privilegedGateway.setPackageOpMode(packageName, name, mode)

                userId == null ->
                    privilegedGateway.setUidOpMode(packageName, name, mode)

                scope == AppOpScope.PACKAGE ->
                    privilegedGateway.setPackageOpModeForUser(
                        userId, packageName, name, mode,
                    )

                else ->
                    privilegedGateway.setUidOpModeForUser(
                        userId, packageName, name, mode,
                    )
            }
        }.getOrNull()?.isSuccessful == true

    private inline fun <T> catchBackendFailure(action: () -> T): Result<T> =
        runCatching(action).onFailure { error ->
            if (error is CancellationException) throw error
        }

    private val ShellCommandResult.isSuccessful: Boolean
        get() = !timedOut && exitCode == 0

    private companion object {
        /** Android's `UserHandle.PER_USER_RANGE`. */
        const val PER_USER_UID_RANGE = 100_000
    }
}
