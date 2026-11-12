package dev.izumi.appopsnext.newapps

import dev.izumi.appopsnext.appops.AdaptiveScopeModeChangeOutcome
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.batch.model.BatchOperationTarget

/**
 * The diagnostic lines of the rules applied to a new app. A template applies
 * a dozen rules or more, so the ones that worked are named in the package's
 * summary line and only a failure gets a line of its own, with every detail.
 */
internal object NewAppRuleLog {
    /** The line of a rule that failed, or null for one that worked. */
    fun failureLine(target: BatchOperationTarget, outcome: AdaptiveScopeModeChangeOutcome): String? {
        val failure = outcome.result as? AppOpModeChangeResult.Failure ?: return null
        return "New-app rule completed. " +
            "package=${target.packageName}, " +
            "operation=${target.stableOperationName}, " +
            "preferredScope=${target.preferredScope.name}, " +
            "resolvedScope=${outcome.appliedScope.name}, " +
            "fallback=${outcome.fallbackAttempted}, " +
            "permissionFlag=${outcome.permissionFlagApplied}, " +
            "result=failure:${failure.phase.name}:${failure.restorationStatus.name}"
    }

    /**
     * A rule that worked as the summary names it: operation, mode and scope,
     * and how it got there when that was not the plain way.
     */
    fun appliedEntry(target: BatchOperationTarget, outcome: AdaptiveScopeModeChangeOutcome): String? {
        val success = outcome.result as? AppOpModeChangeResult.Success ?: return null
        return buildString {
            append(target.stableOperationName)
            append(':')
            append(success.appliedMode.name)
            append('@')
            append(outcome.appliedScope.name)
            if (outcome.fallbackAttempted) append("+fallback")
            if (outcome.permissionFlagApplied) append("+permissionFlag")
        }
    }

    fun progressLine(
        packageName: String,
        restored: Boolean,
        successCount: Int,
        failureCount: Int,
        complete: Boolean,
        applied: List<String>,
    ): String =
        "New-app policy progress. package=$packageName, " +
            "restored=$restored, " +
            "success=$successCount, failure=$failureCount, complete=$complete" +
            (if (applied.isEmpty()) "" else ", applied=[${applied.joinToString(", ")}]")
}
