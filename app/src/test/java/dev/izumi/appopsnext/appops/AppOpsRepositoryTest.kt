package dev.izumi.appopsnext.appops

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpIdentifier
import dev.izumi.appopsnext.appops.model.AppOpsRestorationStatus
import dev.izumi.appopsnext.appops.model.AppOpsWriteTestPhase
import dev.izumi.appopsnext.appops.model.AppOpsWriteTestState
import dev.izumi.appopsnext.appops.model.PackageOpsLoadResult
import dev.izumi.appopsnext.appops.model.ShellCommandResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOpsRepositoryTest {
    private val runInBackground = AppOpIdentifier(
        stableName = "android:run_in_background",
        shellName = "RUN_IN_BACKGROUND",
    )

    @Test
    fun `package load returns a parsed snapshot`() =
        runBlocking {
            val gateway = object : PrivilegedAppOpsGateway {
                override suspend fun getPackageOps(packageName: String) =
                    success("RUN_IN_BACKGROUND: ignore\n")

                override suspend fun getPackageOp(
                    packageName: String,
                    operationName: String,
                ): ShellCommandResult = error("Not used")

                override suspend fun setPackageOpMode(
                    packageName: String,
                    operationName: String,
                    mode: AppOpMode,
                ): ShellCommandResult = error("Not used")
            }

            val result = AppOpsRepository(gateway).loadPackageOps(TEST_PACKAGE)

            assertTrue(result is PackageOpsLoadResult.Success)
            result as PackageOpsLoadResult.Success
            assertEquals(TEST_PACKAGE, result.snapshot.packageName)
            assertEquals("RUN_IN_BACKGROUND", result.snapshot.entries.single().name)
            assertEquals("ignore", result.snapshot.entries.single().mode)
        }

    @Test
    fun `round trip applies verifies and restores the original package mode`() =
        runBlocking {
            val gateway = FakeGateway(
                getResults = ArrayDeque(
                    listOf(
                        success("No operations.\nDefault mode: allow\n"),
                        success("RUN_IN_BACKGROUND: ignore\n"),
                        success("RUN_IN_BACKGROUND: default\n"),
                    ),
                ),
                setResults = ArrayDeque(
                    listOf(success(), success()),
                ),
            )

            val result = AppOpsRepository(gateway).runModeRoundTrip(
                packageName = TEST_PACKAGE,
                operation = runInBackground,
                testMode = AppOpMode.IGNORE,
            )

            assertEquals(
                AppOpsWriteTestState.Success(
                    originalMode = AppOpMode.DEFAULT,
                    testMode = AppOpMode.IGNORE,
                    restoredMode = AppOpMode.DEFAULT,
                ),
                result,
            )
            assertEquals(
                listOf(AppOpMode.IGNORE, AppOpMode.DEFAULT),
                gateway.requestedModes,
            )
        }

    @Test
    fun `verification failure still restores the original mode`() =
        runBlocking {
            val gateway = FakeGateway(
                getResults = ArrayDeque(
                    listOf(
                        success("RUN_IN_BACKGROUND: allow\n"),
                        success("RUN_IN_BACKGROUND: allow\n"),
                        success("RUN_IN_BACKGROUND: allow\n"),
                    ),
                ),
                setResults = ArrayDeque(
                    listOf(success(), success()),
                ),
            )

            val result = AppOpsRepository(gateway).runModeRoundTrip(
                packageName = TEST_PACKAGE,
                operation = runInBackground,
                testMode = AppOpMode.IGNORE,
            )

            assertTrue(result is AppOpsWriteTestState.Failure)
            result as AppOpsWriteTestState.Failure
            assertEquals(AppOpsWriteTestPhase.VERIFY_TEST_MODE, result.phase)
            assertEquals(AppOpMode.ALLOW, result.originalMode)
            assertEquals(
                AppOpsRestorationStatus.SUCCEEDED,
                result.restorationStatus,
            )
            assertEquals(
                listOf(AppOpMode.IGNORE, AppOpMode.ALLOW),
                gateway.requestedModes,
            )
        }

    @Test
    fun `read failure reports that no restoration was required`() =
        runBlocking {
            val gateway = FakeGateway(
                getResults = ArrayDeque(
                    listOf(
                        ShellCommandResult(
                            exitCode = 1,
                            stdout = "",
                            stderr = "package not found",
                            timedOut = false,
                        ),
                    ),
                ),
                setResults = ArrayDeque(),
            )

            val result = AppOpsRepository(gateway).runModeRoundTrip(
                packageName = TEST_PACKAGE,
                operation = runInBackground,
                testMode = AppOpMode.IGNORE,
            )

            assertTrue(result is AppOpsWriteTestState.Failure)
            result as AppOpsWriteTestState.Failure
            assertEquals(AppOpsWriteTestPhase.READ_ORIGINAL, result.phase)
            assertEquals(
                AppOpsRestorationStatus.NOT_REQUIRED,
                result.restorationStatus,
            )
            assertTrue(gateway.requestedModes.isEmpty())
        }

    private class FakeGateway(
        private val getResults: ArrayDeque<ShellCommandResult>,
        private val setResults: ArrayDeque<ShellCommandResult>,
    ) : PrivilegedAppOpsGateway {
        val requestedModes = mutableListOf<AppOpMode>()

        override suspend fun getPackageOps(packageName: String): ShellCommandResult =
            error("Not used by these tests")

        override suspend fun getPackageOp(
            packageName: String,
            operationName: String,
        ): ShellCommandResult = getResults.removeFirst()

        override suspend fun setPackageOpMode(
            packageName: String,
            operationName: String,
            mode: AppOpMode,
        ): ShellCommandResult {
            requestedModes += mode
            return setResults.removeFirst()
        }
    }

    private companion object {
        const val TEST_PACKAGE = "dev.izumi.appopsnext.testtarget"

        fun success(stdout: String = "") = ShellCommandResult(
            exitCode = 0,
            stdout = stdout,
            stderr = "",
            timedOut = false,
        )
    }
}
