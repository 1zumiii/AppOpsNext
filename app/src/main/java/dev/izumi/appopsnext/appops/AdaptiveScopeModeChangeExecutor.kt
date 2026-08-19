package dev.izumi.appopsnext.appops

import dev.izumi.appopsnext.appops.model.AppOpModeChangePhase
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.appops.model.AppOpsRestorationStatus

data class AdaptiveScopeModeChangeOutcome(
    val result: AppOpModeChangeResult,
    val appliedScope: AppOpScope,
    val fallbackAttempted: Boolean,
)

/**
 * Retries a rejected AppOps write through the alternate system scope.
 *
 * UID fallback is allowed only when the UID belongs exclusively to the target
 * package. This prevents a transparent retry from changing sibling packages
 * that happen to share the same UID.
 */
class AdaptiveScopeModeChangeExecutor(
    private val packagesForUid: (Int) -> List<String>,
) {
    suspend fun execute(
        packageName: String,
        uid: Int,
        preferredScope: AppOpScope,
        applyMode: suspend (AppOpScope) -> AppOpModeChangeResult,
    ): AdaptiveScopeModeChangeOutcome {
        val initialResult = applyMode(preferredScope)
        val alternateScope = preferredScope.alternate()
        if (
            !initialResult.isSafeScopeRejection() ||
            !canUseScope(packageName, uid, alternateScope)
        ) {
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
