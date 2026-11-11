package dev.izumi.appopsnext.batch

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpModeChangePhase
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.appops.model.AppOpsRestorationStatus
import dev.izumi.appopsnext.batch.model.BatchOperationItemResult
import dev.izumi.appopsnext.batch.model.BatchOperationReport
import dev.izumi.appopsnext.batch.model.BatchOperationTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class BatchRetryTest {
    private val success = AppOpModeChangeResult.Success(AppOpMode.ALLOW, AppOpMode.IGNORE)
    private val failure = AppOpModeChangeResult.Failure(
        AppOpModeChangePhase.READ_ORIGINAL, null, null, AppOpsRestorationStatus.NOT_REQUIRED,
    )

    private fun target(packageName: String, operation: String = "android:camera") = BatchOperationTarget(
        packageName = packageName,
        appLabel = packageName,
        uid = 10_000,
        stableOperationName = operation,
        preferredScope = AppOpScope.PACKAGE,
        requestedMode = AppOpMode.IGNORE,
    )

    private fun report(vararg results: Pair<BatchOperationTarget, AppOpModeChangeResult>) =
        BatchOperationReport("batch", results.map { BatchOperationItemResult(it.first, it.second) })

    @Test
    fun onlyFailedChangesRunAgain() {
        val first = report(target("a") to success, target("b") to failure, target("c") to failure)

        assertEquals(listOf(target("b"), target("c")), BatchRetry.failedTargets(first))
    }

    @Test
    fun retriedOutcomesReplaceTheEarlierOnesInPlace() {
        val first = report(target("a") to success, target("b") to failure, target("c") to failure)
        val retried = report(target("c") to success, target("b") to failure)

        val merged = BatchRetry.merge(first, retried)

        assertEquals(first.title, merged.title)
        assertEquals(listOf("a", "b", "c"), merged.results.map { it.target.packageName })
        assertEquals(listOf(success, failure, success), merged.results.map { it.result })
        assertEquals(1, merged.failureCount)
    }

    @Test
    fun operationsOfOneAppAreToldApart() {
        val camera = target("a")
        val microphone = target("a", "android:record_audio")
        val first = report(camera to failure, microphone to failure)

        val merged = BatchRetry.merge(first, report(microphone to success))

        assertEquals(listOf(failure, success), merged.results.map { it.result })
    }
}
