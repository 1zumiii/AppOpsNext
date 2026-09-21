package dev.izumi.appopsnext.monitor

import dev.izumi.appopsnext.appops.PrivilegedAppOpsGateway
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.ShellCommandResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundStateProbeTest {
    private class FakeGateway(
        var stdout: String = "",
        var exitCode: Int = 0,
        var failure: Throwable? = null,
    ) : PrivilegedAppOpsGateway {
        var calls = 0

        override suspend fun getUidStates(packageName: String): ShellCommandResult {
            calls++
            failure?.let { throw it }
            return ShellCommandResult(exitCode, stdout, "", false)
        }

        override suspend fun getPackageOps(packageName: String) = unsupported()
        override suspend fun getPackageOp(packageName: String, operationName: String) =
            unsupported()

        override suspend fun setPackageOpMode(
            packageName: String,
            operationName: String,
            mode: AppOpMode,
        ) = unsupported()

        override suspend fun setUidOpMode(
            packageName: String,
            operationName: String,
            mode: AppOpMode,
        ) = unsupported()

        private fun unsupported(): ShellCommandResult = error("not used")
    }

    private val onScreen = """
        Uid u0a601:
          state=top
    """.trimIndent()

    private val offScreen = """
        Uid u0a601:
          state=cch
    """.trimIndent()

    @Test
    fun `the platform state decides whether the app is on screen`() = runBlocking {
        val gateway = FakeGateway(stdout = onScreen)
        val probe = ForegroundStateProbe(gateway, cacheMillis = 1_000L) { 0L }
        assertTrue(probe.isOnScreen(10601, "example.app"))

        val background = ForegroundStateProbe(FakeGateway(stdout = offScreen), 1_000L) { 0L }
        assertFalse(background.isOnScreen(10601, "example.app"))
    }

    /** A uid the dump did not mention is not the one on screen. */
    @Test
    fun `an unknown uid is not on screen`() = runBlocking {
        val probe = ForegroundStateProbe(FakeGateway(stdout = onScreen), 1_000L) { 0L }
        assertFalse(probe.isOnScreen(1010601, "example.app"))
    }

    @Test
    fun `an answer is reused until it goes stale`() = runBlocking {
        val gateway = FakeGateway(stdout = onScreen)
        var now = 0L
        val probe = ForegroundStateProbe(gateway, cacheMillis = 1_000L) { now }
        repeat(20) { probe.isOnScreen(10601, "example.app") }
        assertEquals(1, gateway.calls)
        now = 999
        probe.isOnScreen(10601, "example.app")
        assertEquals(1, gateway.calls)
        now = 1_000
        probe.isOnScreen(10601, "example.app")
        assertEquals(2, gateway.calls)
    }

    @Test
    fun `each package is cached on its own`() = runBlocking {
        val gateway = FakeGateway(stdout = onScreen)
        val probe = ForegroundStateProbe(gateway, cacheMillis = 1_000L) { 0L }
        probe.isOnScreen(10601, "one.app")
        probe.isOnScreen(10601, "two.app")
        probe.isOnScreen(10601, "one.app")
        assertEquals(2, gateway.calls)
    }

    /**
     * A monitor that goes quiet because it could not check something is worse
     * than one that says a little too much, so an unanswerable question reports
     * the access.
     */
    @Test
    fun `a failed query reports the access rather than hiding it`() = runBlocking {
        val thrown = ForegroundStateProbe(
            FakeGateway(failure = IllegalStateException("no backend")),
            1_000L,
        ) { 0L }
        assertFalse(thrown.isOnScreen(10601, "example.app"))

        val failed = ForegroundStateProbe(
            FakeGateway(stdout = onScreen, exitCode = 1),
            1_000L,
        ) { 0L }
        assertFalse(failed.isOnScreen(10601, "example.app"))
    }

    @Test
    fun `a failed query is not cached`() = runBlocking {
        val gateway = FakeGateway(failure = IllegalStateException("no backend"))
        val probe = ForegroundStateProbe(gateway, cacheMillis = 1_000L) { 0L }
        probe.isOnScreen(10601, "example.app")
        probe.isOnScreen(10601, "example.app")
        assertEquals(2, gateway.calls)
    }
}
