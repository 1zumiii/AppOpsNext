package dev.izumi.appopsnext.newapps

import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.batch.model.BatchOperationItemResult
import dev.izumi.appopsnext.batch.model.BatchOperationTarget
import dev.izumi.appopsnext.newapps.model.InstalledPackageFingerprint

internal class NewAppPolicyRuleRunner(
    private val apply: suspend (BatchOperationTarget) -> AppOpModeChangeResult,
    private val save: suspend (NewAppRuleProgress) -> Unit,
    private val canContinue: suspend () -> Boolean,
) {
    suspend fun run(
        installation: InstalledPackageFingerprint,
        targets: List<BatchOperationTarget>,
        previous: List<NewAppRuleProgress>,
    ): Map<String, NewAppRuleProgress> {
        val results = previous.filter { record -> record.installation == installation && targets.any(record::matches) }
            .associateBy { it.item.target.stableOperationName }.toMutableMap()
        for (target in targets) {
            if (!canContinue()) break
            val saved = results[target.stableOperationName]
            if (saved != null && !saved.retryable) continue
            val progress = NewAppRuleProgress(installation, BatchOperationItemResult(target, apply(target)))
            save(progress)
            results[target.stableOperationName] = progress
            if (progress.retryable) break
        }
        return results
    }
}
