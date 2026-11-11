package dev.izumi.appopsnext.batch

import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.batch.model.BatchOperationReport
import dev.izumi.appopsnext.batch.model.BatchOperationTarget

/** Running the failed changes of a finished batch again. */
object BatchRetry {
    fun failedTargets(report: BatchOperationReport): List<BatchOperationTarget> =
        report.results.filter { it.result is AppOpModeChangeResult.Failure }.map { it.target }

    /**
     * [report] with the outcomes of [retried] in place of the earlier ones, in
     * the order the batch first ran. A change that was not run again keeps its
     * outcome.
     */
    fun merge(report: BatchOperationReport, retried: BatchOperationReport): BatchOperationReport {
        val latest = retried.results.associateBy { it.target }
        return report.copy(results = report.results.map { latest[it.target] ?: it })
    }
}
