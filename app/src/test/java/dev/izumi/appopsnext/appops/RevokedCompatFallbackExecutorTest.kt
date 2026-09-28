package dev.izumi.appopsnext.appops

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpModeChangePhase
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.appops.model.AppOpsRestorationStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RevokedCompatFallbackExecutorTest {
    private val executor = RevokedCompatFallbackExecutor()

    @Test
    fun `rejected ignore on a runtime op retries through the flag`() = runBlocking {
        var requestedPermission: String? = null
        val outcome = executor.execute(
            operationName = "COARSE_LOCATION",
            requestedMode = AppOpMode.IGNORE,
            canAffectUid = true,
            appOpsResult = rejected(),
        ) { permission ->
            requestedPermission = permission
            AppOpModeChangeResult.Success(AppOpMode.ALLOW, AppOpMode.IGNORE)
        }

        assertEquals("android.permission.ACCESS_COARSE_LOCATION", requestedPermission)
        assertTrue(outcome.flagApplied)
        assertTrue(outcome.restrictionBlocked)
        assertTrue(outcome.result is AppOpModeChangeResult.Success)
    }

    @Test
    fun `successful app ops writes never touch the flag`() = runBlocking {
        val success = AppOpModeChangeResult.Success(AppOpMode.ALLOW, AppOpMode.IGNORE)
        val outcome = executor.execute(
            operationName = "COARSE_LOCATION",
            requestedMode = AppOpMode.IGNORE,
            canAffectUid = true,
            appOpsResult = success,
        ) { error("flag must not be used") }

        assertSame(success, outcome.result)
        assertFalse(outcome.flagApplied)
        assertFalse(outcome.restrictionBlocked)
    }

    @Test
    fun `ops without a runtime permission keep the original failure`() = runBlocking {
        val failure = rejected()
        val outcome = executor.execute(
            operationName = "RUN_IN_BACKGROUND",
            requestedMode = AppOpMode.IGNORE,
            canAffectUid = true,
            appOpsResult = failure,
        ) { error("flag must not be used") }

        assertSame(failure, outcome.result)
        assertFalse(outcome.restrictionBlocked)
    }

    @Test
    fun `deny and default are left to the existing fallbacks`() = runBlocking {
        for (mode in listOf(AppOpMode.DENY, AppOpMode.DEFAULT)) {
            val failure = rejected()
            val outcome = executor.execute(
                operationName = "CAMERA",
                requestedMode = mode,
                canAffectUid = true,
                appOpsResult = failure,
            ) { error("flag must not be used") }

            assertSame(failure, outcome.result)
            assertFalse(outcome.restrictionBlocked)
        }
    }

    @Test
    fun `shared uid package writes do not fall back to the uid wide flag`() = runBlocking {
        val failure = rejected()
        val outcome = executor.execute(
            operationName = "CAMERA",
            requestedMode = AppOpMode.IGNORE,
            canAffectUid = false,
            appOpsResult = failure,
        ) { error("flag must not be used") }

        assertSame(failure, outcome.result)
        assertTrue(outcome.restrictionBlocked)
    }

    @Test
    fun `unrestored failures are not retried`() = runBlocking {
        val failure = AppOpModeChangeResult.Failure(
            phase = AppOpModeChangePhase.VERIFY_REQUESTED,
            originalMode = AppOpMode.ALLOW,
            observedMode = AppOpMode.ALLOW,
            restorationStatus = AppOpsRestorationStatus.FAILED,
        )
        val outcome = executor.execute(
            operationName = "CAMERA",
            requestedMode = AppOpMode.IGNORE,
            canAffectUid = true,
            appOpsResult = failure,
        ) { error("flag must not be used") }

        assertSame(failure, outcome.result)
        assertFalse(outcome.restrictionBlocked)
    }

    @Test
    fun `inapplicable flag keeps the app ops failure`() = runBlocking {
        val failure = rejected()
        val outcome = executor.execute(
            operationName = "CAMERA",
            requestedMode = AppOpMode.ALLOW,
            canAffectUid = true,
            appOpsResult = failure,
        ) { null }

        assertSame(failure, outcome.result)
        assertFalse(outcome.flagApplied)
        assertFalse(outcome.restrictionBlocked)
    }

    private fun rejected() = AppOpModeChangeResult.Failure(
        phase = AppOpModeChangePhase.VERIFY_REQUESTED,
        originalMode = AppOpMode.ALLOW,
        observedMode = AppOpMode.ALLOW,
        restorationStatus = AppOpsRestorationStatus.SUCCEEDED,
    )
}
