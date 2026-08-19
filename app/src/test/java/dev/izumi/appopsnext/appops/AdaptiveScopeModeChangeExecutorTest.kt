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

class AdaptiveScopeModeChangeExecutorTest {
    @Test
    fun `successful preferred scope is not retried`() = runBlocking {
        val scopes = mutableListOf<AppOpScope>()
        val executor = executorFor("example.app")

        val outcome = executor.execute(
            packageName = "example.app",
            uid = 10_123,
            preferredScope = AppOpScope.PACKAGE,
            requestedMode = AppOpMode.IGNORE,
            readMode = { null },
        ) { scope ->
            scopes += scope
            success()
        }

        assertEquals(listOf(AppOpScope.PACKAGE), scopes)
        assertEquals(AppOpScope.PACKAGE, outcome.appliedScope)
        assertFalse(outcome.fallbackAttempted)
    }

    @Test
    fun `safe package rejection retries unique uid`() = runBlocking {
        val scopes = mutableListOf<AppOpScope>()
        val executor = executorFor("example.app")

        val outcome = executor.execute(
            packageName = "example.app",
            uid = 10_123,
            preferredScope = AppOpScope.PACKAGE,
            requestedMode = AppOpMode.IGNORE,
            readMode = { null },
        ) { scope ->
            scopes += scope
            if (scope == AppOpScope.PACKAGE) rejected() else success()
        }

        assertEquals(
            listOf(AppOpScope.PACKAGE, AppOpScope.UID),
            scopes,
        )
        assertEquals(AppOpScope.UID, outcome.appliedScope)
        assertTrue(outcome.fallbackAttempted)
    }

    @Test
    fun `shared uid prevents transparent uid fallback`() = runBlocking {
        val scopes = mutableListOf<AppOpScope>()
        val executor = AdaptiveScopeModeChangeExecutor {
            listOf("example.app", "sibling.app")
        }

        val outcome = executor.execute(
            packageName = "example.app",
            uid = 10_123,
            preferredScope = AppOpScope.PACKAGE,
            requestedMode = AppOpMode.IGNORE,
            readMode = { null },
        ) { scope ->
            scopes += scope
            rejected()
        }

        assertEquals(listOf(AppOpScope.PACKAGE), scopes)
        assertEquals(AppOpScope.PACKAGE, outcome.appliedScope)
        assertFalse(outcome.fallbackAttempted)
    }

    @Test
    fun `shared uid already at requested mode counts as success`() =
        runBlocking {
            val appliedScopes = mutableListOf<AppOpScope>()
            val readScopes = mutableListOf<AppOpScope>()
            val executor = AdaptiveScopeModeChangeExecutor {
                listOf("example.app", "sibling.app")
            }

            val outcome = executor.execute(
                packageName = "example.app",
                uid = 10_123,
                preferredScope = AppOpScope.PACKAGE,
                requestedMode = AppOpMode.IGNORE,
                readMode = { scope ->
                    readScopes += scope
                    AppOpMode.IGNORE
                },
            ) { scope ->
                appliedScopes += scope
                rejected()
            }

            assertEquals(listOf(AppOpScope.PACKAGE), appliedScopes)
            assertEquals(listOf(AppOpScope.UID), readScopes)
            assertEquals(AppOpScope.UID, outcome.appliedScope)
            assertEquals(
                AppOpModeChangeResult.Success(
                    originalMode = AppOpMode.IGNORE,
                    appliedMode = AppOpMode.IGNORE,
                ),
                outcome.result,
            )
            assertTrue(outcome.fallbackAttempted)
        }

    @Test
    fun `safe uid rejection can fall back to package scope`() = runBlocking {
        val scopes = mutableListOf<AppOpScope>()
        val executor = AdaptiveScopeModeChangeExecutor {
            listOf("example.app", "sibling.app")
        }

        val outcome = executor.execute(
            packageName = "example.app",
            uid = 10_123,
            preferredScope = AppOpScope.UID,
            requestedMode = AppOpMode.IGNORE,
            readMode = { null },
        ) { scope ->
            scopes += scope
            if (scope == AppOpScope.UID) rejected() else success()
        }

        assertEquals(
            listOf(AppOpScope.UID, AppOpScope.PACKAGE),
            scopes,
        )
        assertEquals(AppOpScope.PACKAGE, outcome.appliedScope)
        assertTrue(outcome.fallbackAttempted)
    }

    @Test
    fun `unsafe failure is never retried`() = runBlocking {
        val scopes = mutableListOf<AppOpScope>()
        val executor = executorFor("example.app")
        val unsafeFailure = AppOpModeChangeResult.Failure(
            phase = AppOpModeChangePhase.RESTORE_ORIGINAL,
            originalMode = AppOpMode.DEFAULT,
            observedMode = null,
            restorationStatus = AppOpsRestorationStatus.FAILED,
        )

        val outcome = executor.execute(
            packageName = "example.app",
            uid = 10_123,
            preferredScope = AppOpScope.PACKAGE,
            requestedMode = AppOpMode.IGNORE,
            readMode = { null },
        ) { scope ->
            scopes += scope
            unsafeFailure
        }

        assertEquals(listOf(AppOpScope.PACKAGE), scopes)
        assertEquals(unsafeFailure, outcome.result)
        assertFalse(outcome.fallbackAttempted)
    }

    private fun executorFor(packageName: String) =
        AdaptiveScopeModeChangeExecutor { listOf(packageName) }

    private fun success() = AppOpModeChangeResult.Success(
        originalMode = AppOpMode.DEFAULT,
        appliedMode = AppOpMode.IGNORE,
    )

    private fun rejected() = AppOpModeChangeResult.Failure(
        phase = AppOpModeChangePhase.VERIFY_REQUESTED,
        originalMode = AppOpMode.DEFAULT,
        observedMode = AppOpMode.ALLOW,
        restorationStatus = AppOpsRestorationStatus.SUCCEEDED,
    )
}
