package dev.izumi.appopsnext.appops

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpModeChangePhase
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.appops.model.AppOpsRestorationStatus

data class AdaptiveScopeModeChangeOutcome(
    val result: AppOpModeChangeResult,
    val appliedScope: AppOpScope,
    val fallbackAttempted: Boolean,
    /** The result came from the permission's `REVOKED_COMPAT` flag. */
    val permissionFlagApplied: Boolean = false,
    /** See [RevokedCompatFallbackOutcome.restrictionBlocked]. */
    val restrictionBlocked: Boolean = false,
)

/** Lets a rejected runtime-permission write retry through the permission flag. */
class RevokedCompatRetry(
    val operationName: String,
    val apply: suspend (permissionName: String) -> AppOpModeChangeResult?,
)

/**
 * Retries a rejected AppOps write through the alternate system scope.
 *
 * UID fallback is allowed only when the UID belongs exclusively to the target
 * package. This prevents a transparent retry from changing sibling packages
 * that happen to share the same UID. The permission flag retry follows the
 * same rule because the flag also covers the whole UID.
 */
class AdaptiveScopeModeChangeExecutor(
    private val packagesForUid: (Int) -> List<String>,
) {
    private val revokedCompatExecutor = RevokedCompatFallbackExecutor()

    suspend fun execute(
        packageName: String,
        uid: Int,
        preferredScope: AppOpScope,
        requestedMode: AppOpMode,
        readMode: suspend (AppOpScope) -> AppOpMode?,
        revokedCompatRetry: RevokedCompatRetry? = null,
        applyMode: suspend (AppOpScope) -> AppOpModeChangeResult,
    ): AdaptiveScopeModeChangeOutcome {
        val scopeOutcome = executeScopes(
            packageName, uid, preferredScope, requestedMode, readMode, applyMode,
        )
        revokedCompatRetry ?: return scopeOutcome

        val flagOutcome = revokedCompatExecutor.execute(
            operationName = revokedCompatRetry.operationName,
            requestedMode = requestedMode,
            canAffectUid = preferredScope == AppOpScope.UID ||
                canUseScope(packageName, uid, AppOpScope.UID),
            appOpsResult = scopeOutcome.result,
            applyFlag = revokedCompatRetry.apply,
        )
        return scopeOutcome.copy(
            result = flagOutcome.result,
            appliedScope = if (flagOutcome.flagApplied) {
                AppOpScope.UID
            } else {
                scopeOutcome.appliedScope
            },
            permissionFlagApplied = flagOutcome.flagApplied,
            restrictionBlocked = flagOutcome.restrictionBlocked,
        )
    }

    private suspend fun executeScopes(
        packageName: String,
        uid: Int,
        preferredScope: AppOpScope,
        requestedMode: AppOpMode,
        readMode: suspend (AppOpScope) -> AppOpMode?,
        applyMode: suspend (AppOpScope) -> AppOpModeChangeResult,
    ): AdaptiveScopeModeChangeOutcome {
        val alternateScope = preferredScope.alternate()
        if (
            preferredScope == AppOpScope.PACKAGE &&
            requestedMode != AppOpMode.DEFAULT &&
            readMode(alternateScope) == requestedMode
        ) {
            return alreadySatisfied(
                mode = requestedMode,
                scope = alternateScope,
            )
        }

        val initialResult = applyMode(preferredScope)
        if (!initialResult.isSafeScopeRejection()) {
            return AdaptiveScopeModeChangeOutcome(
                result = initialResult,
                appliedScope = preferredScope,
                fallbackAttempted = false,
            )
        }

        // An explicit UID mode takes precedence over the package mode. If a
        // UID write is rejected, changing the covered package record cannot
        // satisfy the requested effective state.
        // DEFAULT clears a record in the requested scope. An absent/default
        // alternate record cannot prove that this reset succeeded.
        if (preferredScope == AppOpScope.UID || requestedMode == AppOpMode.DEFAULT) {
            return AdaptiveScopeModeChangeOutcome(
                result = initialResult,
                appliedScope = preferredScope,
                fallbackAttempted = false,
            )
        }

        if (!canUseScope(packageName, uid, alternateScope)) {
            val alternateMode = readMode(alternateScope)
            if (alternateMode == requestedMode) {
                return alreadySatisfied(
                    mode = alternateMode,
                    scope = alternateScope,
                )
            }
            return AdaptiveScopeModeChangeOutcome(
                result = initialResult,
                appliedScope = preferredScope,
                fallbackAttempted = false,
            )
        }

        return AdaptiveScopeModeChangeOutcome(
            result = applyMode(alternateScope),
            appliedScope = alternateScope,
            fallbackAttempted = true,
        )
    }

    private fun alreadySatisfied(
        mode: AppOpMode,
        scope: AppOpScope,
    ) = AdaptiveScopeModeChangeOutcome(
        result = AppOpModeChangeResult.Success(
            originalMode = mode,
            appliedMode = mode,
        ),
        appliedScope = scope,
        fallbackAttempted = true,
    )

    private fun canUseScope(
        packageName: String,
        uid: Int,
        scope: AppOpScope,
    ): Boolean = when (scope) {
        AppOpScope.PACKAGE -> true
        AppOpScope.UID -> packagesForUid(uid)
            .distinct()
            .singleOrNull() == packageName
    }

    private fun AppOpScope.alternate(): AppOpScope = when (this) {
        AppOpScope.PACKAGE -> AppOpScope.UID
        AppOpScope.UID -> AppOpScope.PACKAGE
    }

    private fun AppOpModeChangeResult.isSafeScopeRejection(): Boolean =
        this is AppOpModeChangeResult.Failure &&
            phase in ScopeRejectionPhases &&
            restorationStatus == AppOpsRestorationStatus.SUCCEEDED

    private companion object {
        val ScopeRejectionPhases = setOf(
            AppOpModeChangePhase.APPLY_REQUESTED,
            AppOpModeChangePhase.VERIFY_REQUESTED,
        )
    }
}
