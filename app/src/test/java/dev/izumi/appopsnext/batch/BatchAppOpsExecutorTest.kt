package dev.izumi.appopsnext.batch

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpModeChangePhase
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.appops.model.AppOpsRestorationStatus
import dev.izumi.appopsnext.batch.model.BatchOperationTarget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class BatchAppOpsExecutorTest {
    @Test
    fun `executor retains every success and failure in target order`() =
        runBlocking {
            val targets = listOf(
                target("android:camera"),
                target("android:record_audio"),
            )
            val progress = mutableListOf<Pair<Int, Int>>()
            val executor = BatchAppOpsExecutor { target ->
                if (target == targets.first()) {
                    AppOpModeChangeResult.Success(
                        originalMode = AppOpMode.DEFAULT,
                        appliedMode = AppOpMode.IGNORE,
                    )
                } else {
                    AppOpModeChangeResult.Failure(
                        phase = AppOpModeChangePhase.VERIFY_REQUESTED,
                        originalMode = AppOpMode.DEFAULT,
                        observedMode = AppOpMode.DEFAULT,
                        restorationStatus =
                            AppOpsRestorationStatus.SUCCEEDED,
                    )
                }
            }

            val report = executor.execute(
                title = "Privacy",
                targets = targets,
                onProgress = { completed, total ->
                    progress += completed to total
                },
            )

            assertEquals(targets, report.results.map { it.target })
            assertEquals(1, report.successCount)
            assertEquals(1, report.failureCount)
            assertEquals(listOf(1 to 2, 2 to 2), progress)
        }

    private fun target(operationName: String) = BatchOperationTarget(
        packageName = "example.app",
        appLabel = "Example",
        stableOperationName = operationName,
        scope = AppOpScope.UID,
        requestedMode = AppOpMode.IGNORE,
    )
}
