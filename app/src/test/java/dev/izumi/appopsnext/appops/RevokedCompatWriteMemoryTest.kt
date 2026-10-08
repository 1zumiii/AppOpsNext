package dev.izumi.appopsnext.appops

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpModeChangePhase
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.appops.model.AppOpsRestorationStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RevokedCompatWriteMemoryTest {
    private val memory = RevokedCompatWriteMemory()
    private var packages = listOf("example.app")
    private var writes = 0
    private var flags = 0
    private val rejection = AppOpModeChangeResult.Failure(
        phase = AppOpModeChangePhase.VERIFY_REQUESTED,
        originalMode = AppOpMode.ALLOW,
        observedMode = AppOpMode.ALLOW,
        restorationStatus = AppOpsRestorationStatus.SUCCEEDED,
    )

    @Test
    fun `confirmed flag retry is shared across executors without repeating appops writes`() = runBlocking {
        val first = change()
        assertEquals(2, writes)
        assertTrue(first.permissionFlagApplied)
        val second = change(mode = AppOpMode.ALLOW, observed = AppOpMode.IGNORE)
        assertEquals(2, writes)
        assertEquals(2, flags)
        assertTrue(second.permissionFlagApplied)
        assertEquals(AppOpScope.UID, second.appliedScope)
        assertEquals(AppOpMode.ALLOW, (second.result as AppOpModeChangeResult.Success).appliedMode)
    }

    @Test
    fun `learned target already at requested mode requires no writes`() = runBlocking {
        change()
        change(observed = AppOpMode.IGNORE)
        assertEquals(2, writes)
        assertEquals(1, flags)
    }

    @Test
    fun `generic write errors and failed flag attempts are not learned`() = runBlocking {
        change(appOpsResult = rejection.copy(phase = AppOpModeChangePhase.APPLY_REQUESTED))
        assertFalse(memory.contains("example.app", 10123, "CAMERA"))
        change(flagResult = rejection)
        assertFalse(memory.contains("example.app", 10123, "CAMERA"))
    }

    @Test
    fun `missing or restricted observed mode does not establish mapping`() = runBlocking {
        for (observed in listOf(null, AppOpMode.IGNORE, AppOpMode.DENY, AppOpMode.DEFAULT)) {
            change(appOpsResult = rejection.copy(observedMode = observed))
            assertFalse(memory.contains("example.app", 10123, "CAMERA"))
        }
    }

    @Test
    fun `unsafe restoration never reaches the flag or establishes memory`() = runBlocking {
        change(appOpsResult = rejection.copy(restorationStatus = AppOpsRestorationStatus.FAILED))
        assertEquals(0, flags)
        assertFalse(memory.contains("example.app", 10123, "CAMERA"))
    }

    @Test
    fun `learning is limited to the same package uid and operation`() = runBlocking {
        change()
        assertTrue(memory.contains("example.app", 10123, "android:camera"))
        assertFalse(memory.contains("other.app", 10123, "CAMERA"))
        assertFalse(memory.contains("example.app", 110123, "CAMERA"))
        assertFalse(memory.contains("example.app", 10123, "RECORD_AUDIO"))
        assertFalse(RevokedCompatWriteMemory().contains("example.app", 10123, "CAMERA"))
        change(operation = "RECORD_AUDIO")
        assertEquals(4, writes)
    }

    @Test
    fun `shared uid safety is checked again after learning`() = runBlocking {
        change()
        packages = listOf("example.app", "sibling.app")
        change()
        assertEquals(3, writes)
        assertEquals(1, flags)
        change(scope = AppOpScope.UID)
        assertEquals(3, writes)
        assertEquals(2, flags)
    }

    @Test
    fun `deny and default keep their original scope semantics`() = runBlocking {
        change()
        change(mode = AppOpMode.DENY)
        change(mode = AppOpMode.DEFAULT)
        assertEquals(5, writes)
        assertEquals(1, flags)
    }

    @Test
    fun `unavailable direct flag path forgets evidence and resumes normal writes`() = runBlocking {
        change()
        change(flagResult = null)
        assertEquals(4, writes)
        assertFalse(memory.contains("example.app", 10123, "CAMERA"))
    }

    @Test
    fun `failed direct flag restoration is returned without any further write`() = runBlocking {
        change()
        val failure = rejection.copy(restorationStatus = AppOpsRestorationStatus.FAILED)
        val result = change(flagResult = failure)
        assertEquals(failure, result.result)
        assertEquals(2, writes)
        assertFalse(result.permissionFlagApplied)
        assertFalse(memory.contains("example.app", 10123, "CAMERA"))
    }

    private suspend fun change(
        mode: AppOpMode = AppOpMode.IGNORE,
        observed: AppOpMode = AppOpMode.ALLOW,
        operation: String = "CAMERA",
        scope: AppOpScope = AppOpScope.PACKAGE,
        appOpsResult: AppOpModeChangeResult = rejection,
        flagResult: AppOpModeChangeResult? = AppOpModeChangeResult.Success(observed, mode),
    ): AdaptiveScopeModeChangeOutcome = AdaptiveScopeModeChangeExecutor(memory) { packages }.execute(
        packageName = "example.app",
        uid = 10123,
        preferredScope = scope,
        requestedMode = mode,
        readMode = { observed },
        revokedCompatRetry = RevokedCompatRetry(operation) {
            flags++
            flagResult
        },
        applyMode = {
            writes++
            appOpsResult
        },
    )
}
