package dev.izumi.appopsnext.batch

import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.batch.model.BatchOperationItemResult
import dev.izumi.appopsnext.batch.model.BatchOperationReport
import dev.izumi.appopsnext.batch.model.BatchOperationTarget

class BatchAppOpsExecutor(
    private val applyTarget:
        suspend (BatchOperationTarget) -> AppOpModeChangeResult,
) {
    suspend fun execute(
        title: String,
        targets: List<BatchOperationTarget>,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): BatchOperationReport {
        val results = mutableListOf<BatchOperationItemResult>()
        targets.forEachIndexed { index, target ->
            val result = applyTarget(target)
            results += BatchOperationItemResult(target, result)
            onProgress(index + 1, targets.size)
        }
        return BatchOperationReport(title, results)
    }
}
