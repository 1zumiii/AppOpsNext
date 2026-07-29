package dev.izumi.appopsnext.presentation.app_detail

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpModeChangePhase
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.appops.model.AppOpsRestorationStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DenyFallbackModeChangeExecutorTest {
    @Test
    fun `successful deny is kept without fallback`() = runBlocking {
        val requestedModes = mutableListOf<AppOpMode>()
        val outcome = DenyFallbackModeChangeExecutor { mode ->
            requestedModes += mode
            success(mode)
        }.execute(AppOpMode.DENY)

        assertEquals(listOf(AppOpMode.DENY), requestedModes)
        assertEquals(success(AppOpMode.DENY), outcome.result)
        assertFalse(outcome.denyFallbackAttempted)
    }

    @Test
    fun `rejected deny safely falls back to ignore`() = runBlocking {
        val requestedModes = mutableListOf<AppOpMode>()
        val outcome = DenyFallbackModeChangeExecutor { mode ->
            requestedModes += mode
            if (mode == AppOpMode.DENY) {
                rejectedMode()
            } else {
                success(mode)
            }
        }.execute(AppOpMode.DENY)

        assertEquals(
            listOf(AppOpMode.DENY, AppOpMode.IGNORE),
            requestedModes,
        )
        assertEquals(success(AppOpMode.IGNORE), outcome.result)
        assertTrue(outcome.denyFallbackAttempted)
    }

    @Test
    fun `non deny failures never change the requested mode`() = runBlocking {
        val requestedModes = mutableListOf<AppOpMode>()
        val failure = rejectedMode()
        val outcome = DenyFallbackModeChangeExecutor { mode ->
            requestedModes += mode
            failure
        }.execute(AppOpMode.FOREGROUND)

        assertEquals(listOf(AppOpMode.FOREGROUND), requestedModes)
        assertEquals(failure, outcome.result)
        assertFalse(outcome.denyFallbackAttempted)
    }

    @Test
    fun `unsafe restoration failure prevents deny fallback`() = runBlocking {
        val requestedModes = mutableListOf<AppOpMode>()
        val unsafeFailure = AppOpModeChangeResult.Failure(
            phase = AppOpModeChangePhase.RESTORE_ORIGINAL,
            originalMode = AppOpMode.DEFAULT,
            observedMode = null,
            restorationStatus = AppOpsRestorationStatus.FAILED,
        )
        val outcome = DenyFallbackModeChangeExecutor { mode ->
            requestedModes += mode
            unsafeFailure
        }.execute(AppOpMode.DENY)

        assertEquals(listOf(AppOpMode.DENY), requestedModes)
        assertEquals(unsafeFailure, outcome.result)
        assertFalse(outcome.denyFallbackAttempted)
    }

    private fun success(mode: AppOpMode) =
        AppOpModeChangeResult.Success(
            originalMode = AppOpMode.DEFAULT,
            appliedMode = mode,
        )

    private fun rejectedMode() = AppOpModeChangeResult.Failure(
        phase = AppOpModeChangePhase.VERIFY_REQUESTED,
        originalMode = AppOpMode.DEFAULT,
        observedMode = AppOpMode.ALLOW,
        restorationStatus = AppOpsRestorationStatus.SUCCEEDED,
    )
}
