package dev.izumi.appopsnext.appops

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpIdentifier
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.appops.model.PackageOpsLoadResult
import dev.izumi.appopsnext.appops.model.ShellCommandResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiUserRepositoryTest {
    private val camera = AppOpIdentifier(stableName = "android:camera", shellName = "CAMERA")

    @Test
    fun `a null user keeps the current user commands`() = runBlocking {
        val gateway = RecordingGateway()

        AppOpsRepository(gateway).loadPackageOps(PACKAGE)

        assertEquals(listOf("getPackageOps $PACKAGE"), gateway.calls)
    }

    @Test
    fun `another user reads through the user scoped command`() = runBlocking {
        val gateway = RecordingGateway()

        val result = AppOpsRepository(gateway).loadPackageOps(PACKAGE, userId = 10)

        assertTrue(result is PackageOpsLoadResult.Success)
        assertEquals(listOf("getPackageOpsForUser 10 $PACKAGE"), gateway.calls)
    }

    @Test
    fun `read mode for another user names the user`() = runBlocking {
        val gateway = RecordingGateway(stdout = "CAMERA: ignore\n")

        val mode = AppOpsRepository(gateway).readMode(PACKAGE, camera, AppOpScope.PACKAGE, 10)

        assertEquals(AppOpMode.IGNORE, mode)
        assertEquals(listOf("getPackageOpForUser 10 $PACKAGE CAMERA"), gateway.calls)
    }

    @Test
    fun `a package write for another user verifies and writes with that user`() = runBlocking {
        val gateway = RecordingGateway(stdout = "CAMERA: allow\n")
        val repository = AppOpsRepository(gateway)

        val result = repository.applyMode(PACKAGE, camera, AppOpScope.PACKAGE, AppOpMode.IGNORE, userId = 10)

        // The fake keeps answering allow, so the verify step fails and the change is undone.
        assertTrue(result is AppOpModeChangeResult.Failure)
        assertEquals(
            listOf(
                "getPackageOpForUser 10 $PACKAGE CAMERA",
                "setPackageOpModeForUser 10 $PACKAGE CAMERA ignore",
                "getPackageOpForUser 10 $PACKAGE CAMERA",
            ),
            gateway.calls.take(3),
        )
        assertTrue(gateway.calls.none { it.startsWith("setPackageOpMode ") })
    }

    @Test
    fun `a uid write for another user uses the uid user command`() = runBlocking {
        val gateway = RecordingGateway(stdout = "Uid mode: CAMERA: allow\n")

        AppOpsRepository(gateway).applyMode(PACKAGE, camera, AppOpScope.UID, AppOpMode.IGNORE, userId = 999)

        assertTrue(gateway.calls.contains("setUidOpModeForUser 999 $PACKAGE CAMERA ignore"))
        assertTrue(gateway.calls.none { it.startsWith("setUidOpMode ") })
    }

    private class RecordingGateway(
        private val stdout: String = "",
    ) : PrivilegedAppOpsGateway {
        val calls = mutableListOf<String>()

        private fun ok() = ShellCommandResult(exitCode = 0, stdout = stdout, stderr = "", timedOut = false)

        override suspend fun getPackageOps(packageName: String): ShellCommandResult {
            calls += "getPackageOps $packageName"
            return ok()
        }

        override suspend fun getPackageOp(packageName: String, operationName: String): ShellCommandResult {
            calls += "getPackageOp $packageName $operationName"
            return ok()
        }

        override suspend fun getPackageOpsForUser(userId: Int, packageName: String): ShellCommandResult {
            calls += "getPackageOpsForUser $userId $packageName"
            return ok()
        }

        override suspend fun getPackageOpForUser(
            userId: Int,
            packageName: String,
            operationName: String,
        ): ShellCommandResult {
            calls += "getPackageOpForUser $userId $packageName $operationName"
            return ok()
        }

        override suspend fun setPackageOpMode(
            packageName: String,
            operationName: String,
            mode: AppOpMode,
        ): ShellCommandResult {
            calls += "setPackageOpMode $packageName $operationName ${mode.shellValue}"
            return ok()
        }

        override suspend fun setUidOpMode(
            packageName: String,
            operationName: String,
            mode: AppOpMode,
        ): ShellCommandResult {
            calls += "setUidOpMode $packageName $operationName ${mode.shellValue}"
            return ok()
        }

        override suspend fun setPackageOpModeForUser(
            userId: Int,
            packageName: String,
            operationName: String,
            mode: AppOpMode,
        ): ShellCommandResult {
            calls += "setPackageOpModeForUser $userId $packageName $operationName ${mode.shellValue}"
            return ok()
        }

        override suspend fun setUidOpModeForUser(
            userId: Int,
            packageName: String,
            operationName: String,
            mode: AppOpMode,
        ): ShellCommandResult {
            calls += "setUidOpModeForUser $userId $packageName $operationName ${mode.shellValue}"
            return ok()
        }
    }

    private companion object {
        const val PACKAGE = "com.example.target"
    }
}
