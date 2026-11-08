package dev.izumi.appopsnext.appops.changes

import dev.izumi.appopsnext.appops.AppOpsRepository
import dev.izumi.appopsnext.appops.PrivilegedAppOpsGateway
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpIdentifier
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.appops.model.ShellCommandResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOpsRepositoryChangeReportTest {
    private class Gateway(var mode: AppOpMode, val acceptWrites: Boolean = true) : PrivilegedAppOpsGateway {
        override suspend fun getPackageOps(packageName: String) = error("unused")
        override suspend fun getPackageOp(packageName: String, operationName: String) =
            ShellCommandResult(0, "RUN_IN_BACKGROUND: ${mode.shellValue}", "", false)
        override suspend fun setPackageOpMode(
            packageName: String,
            operationName: String,
            mode: AppOpMode,
        ): ShellCommandResult {
            if (acceptWrites) this.mode = mode
            return ShellCommandResult(0, "", "", false)
        }
        override suspend fun setUidOpMode(packageName: String, operationName: String, mode: AppOpMode) =
            error("unused")
    }

    private fun repository(gateway: Gateway, reported: MutableList<AppOpModeChange>) =
        AppOpsRepository(gateway, onModeChanged = { reported += it })

    @Test fun `a verified change is reported with the mode before and after`() = runBlocking {
        val reported = mutableListOf<AppOpModeChange>()
        val result = repository(Gateway(AppOpMode.ALLOW), reported)
            .applyMode(PACKAGE, OPERATION, AppOpScope.PACKAGE, AppOpMode.IGNORE)
        assertTrue(result is AppOpModeChangeResult.Success)
        assertEquals(
            listOf(
                AppOpModeChange(
                    PACKAGE, null, OPERATION, AppOpScope.PACKAGE,
                    originalMode = AppOpMode.ALLOW, appliedMode = AppOpMode.IGNORE,
                ),
            ),
            reported,
        )
    }

    @Test fun `a write that changes nothing is not reported`() = runBlocking {
        val reported = mutableListOf<AppOpModeChange>()
        repository(Gateway(AppOpMode.IGNORE), reported)
            .applyMode(PACKAGE, OPERATION, AppOpScope.PACKAGE, AppOpMode.IGNORE)
        assertTrue(reported.isEmpty())
    }

    @Test fun `a write the system did not take is not reported`() = runBlocking {
        val reported = mutableListOf<AppOpModeChange>()
        val result = repository(Gateway(AppOpMode.ALLOW, acceptWrites = false), reported)
            .applyMode(PACKAGE, OPERATION, AppOpScope.PACKAGE, AppOpMode.IGNORE)
        assertTrue(result is AppOpModeChangeResult.Failure)
        assertTrue(reported.isEmpty())
    }

    @Test fun `a failing change log does not fail the write`() = runBlocking {
        val result = AppOpsRepository(Gateway(AppOpMode.ALLOW), onModeChanged = { error("disk full") })
            .applyMode(PACKAGE, OPERATION, AppOpScope.PACKAGE, AppOpMode.IGNORE)
        assertTrue(result is AppOpModeChangeResult.Success)
    }

    private companion object {
        const val PACKAGE = "com.example.target"
        val OPERATION = AppOpIdentifier("android:run_in_background", "RUN_IN_BACKGROUND")
    }
}
