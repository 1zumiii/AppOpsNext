package dev.izumi.appopsnext.history

import dev.izumi.appopsnext.appops.PrivilegedAppOpsGateway
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.ShellCommandResult
import dev.izumi.appopsnext.history.model.AppOpHistoryLoadResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOpsHistoryRepositoryTest {
    @Test
    fun `falls back to aggregate snapshots when discrete history is empty`() =
        runBlocking {
            val repository = AppOpsHistoryRepository(
                FakeGateway(
                    """
                        Aggregated accesses:
                          snapshot:
                            begin = 2026-07-29 09:29:47.516
                            end = 2026-07-29 09:44:47.516
                            Uid u0a166:
                              Package com.example.keyboard:
                                Attribution null:
                                  READ_CLIPBOARD:
                                    [bg-s] = access=3
                        Discrete accesses:
                          Largest chain id: 0
                    """.trimIndent(),
                ),
            )

            val result = repository.loadOperationHistory("READ_CLIPBOARD")

            assertTrue(result is AppOpHistoryLoadResult.Success)
            result as AppOpHistoryLoadResult.Success
            assertEquals(1, result.events.size)
            assertEquals(3, result.events.single().accessCount)
            assertTrue(result.events.single().isAggregated)
        }

    @Test
    fun `prefers discrete records when Android provides them`() =
        runBlocking {
            val repository = AppOpsHistoryRepository(
                FakeGateway(
                    """
                        Aggregated accesses:
                          snapshot:
                            begin = 2026-07-29 09:29:47.516
                            end = 2026-07-29 09:44:47.516
                            Uid u0a166:
                              Package com.example.camera:
                                Attribution null:
                                  CAMERA:
                                    [top-s] = access=8
                        Discrete accesses:
                          Uid: 10166
                            Package: com.example.camera
                              CAMERA
                                Attribution: null
                                  Access [top-s] at 2026-07-29 09:43:00.000
                    """.trimIndent(),
                ),
            )

            val result = repository.loadOperationHistory("CAMERA")

            result as AppOpHistoryLoadResult.Success
            assertEquals(1, result.events.size)
            assertEquals(1, result.events.single().accessCount)
            assertTrue(!result.events.single().isAggregated)
        }

    private class FakeGateway(
        private val historyOutput: String,
    ) : PrivilegedAppOpsGateway {
        override suspend fun getPackageOps(
            packageName: String,
        ): ShellCommandResult = error("Not used")

        override suspend fun getPackageOp(
            packageName: String,
            operationName: String,
        ): ShellCommandResult = error("Not used")

        override suspend fun getHistory(
            operationName: String,
        ): ShellCommandResult = ShellCommandResult(
            exitCode = 0,
            stdout = historyOutput,
            stderr = "",
            timedOut = false,
        )

        override suspend fun setPackageOpMode(
            packageName: String,
            operationName: String,
            mode: AppOpMode,
        ): ShellCommandResult = error("Not used")

        override suspend fun setUidOpMode(
            packageName: String,
            operationName: String,
            mode: AppOpMode,
        ): ShellCommandResult = error("Not used")
    }
}
