package dev.izumi.appopsnext.history

import dev.izumi.appopsnext.appops.PrivilegedAppOpsGateway
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.ShellCommandResult
import dev.izumi.appopsnext.history.model.AppOpHistoryLoadResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class AppOpsHistoryRepositoryTest {
    @Test fun `keeps aggregate rejections beside discrete accesses without duplicate access counts`() = runBlocking {
        val repository = repository(at("2026-09-22 12:00:00.000"), """
            Aggregated accesses:
              snapshot:
                begin = 2026-09-22 09:00:00.000
                end = 2026-09-22 10:00:00.000
                Uid u0a166:
                  Package com.example.camera:
                    Attribution null:
                      CAMERA:
                        [top-s] = access=8, reject=2, duration=+1s
                        [bg-s] = reject=3
            Discrete accesses:
              Uid: 10166
                Package: com.example.camera
                  CAMERA
                    Attribution: null
                      Access [top-s] at 2026-09-22 09:43:00.000
        """)
        val result = repository.loadOperationHistory("CAMERA") as AppOpHistoryLoadResult.Success
        assertEquals(3, result.events.size)
        assertEquals(1, result.events.sumOf { it.accessCount })
        assertEquals(5, result.events.sumOf { it.rejectCount })
        assertTrue(result.events.filter { it.isAggregated }.all { it.accessCount == 0 && it.durationMillis == null })
        assertTrue(result.events.filter { !it.isAggregated }.all { it.rejectCount == 0 })
    }

    @Test
    fun `falls back to aggregate snapshots when discrete history is empty`() =
        runBlocking {
            val repository = repository(
                at("2026-07-29 12:00:00.000"),
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
                    """,
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
            val repository = repository(
                at("2026-07-29 12:00:00.000"),
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
                    """,
            )

            val result = repository.loadOperationHistory("CAMERA")

            result as AppOpHistoryLoadResult.Success
            assertEquals(1, result.events.size)
            assertEquals(1, result.events.single().accessCount)
            assertTrue(!result.events.single().isAggregated)
        }

    @Test fun `drops intervals outside the offered window, including impossible dates`() = runBlocking {
        val repository = repository(at("2026-09-22 12:00:00.000"), """
            Aggregated accesses:
              snapshot:
                begin = 2026-09-22 09:00:00.000
                end = 2026-09-22 10:00:00.000
                Uid u0a166:
                  Package org.thoughtcrime.securesms:
                    Attribution null:
                      FINE_LOCATION:
                        [bg-s] = reject=2
              snapshot:
                begin = 1946-03-11 15:55:54.592
                end = 1956-01-17 20:20:38.817
                Uid u0a166:
                  Package org.thoughtcrime.securesms:
                    Attribution null:
                      FINE_LOCATION:
                        [bg-s] = reject=1780
            Discrete accesses:
              Largest chain id: 0
        """)
        val result = repository.loadOperationHistory("FINE_LOCATION") as AppOpHistoryLoadResult.Success
        assertEquals(2, result.events.single().rejectCount)
    }

    @Test fun `intervals older than individual records keep their accesses`() = runBlocking {
        val repository = repository(at("2026-09-22 12:00:00.000"), """
            Aggregated accesses:
              snapshot:
                begin = 2026-09-21 09:00:00.000
                end = 2026-09-21 10:00:00.000
                Uid u0a166:
                  Package com.example.camera:
                    Attribution null:
                      CAMERA:
                        [top-s] = access=4
              snapshot:
                begin = 2026-09-05 00:00:00.000
                end = 2026-09-10 00:00:00.000
                Uid u0a166:
                  Package com.example.camera:
                    Attribution null:
                      CAMERA:
                        [top-s] = access=6, reject=1
            Discrete accesses:
              Uid: 10166
                Package: com.example.camera
                  CAMERA
                    Attribution: null
                      Access [top-s] at 2026-09-21 09:43:00.000
        """)
        val result = repository.loadOperationHistory("CAMERA") as AppOpHistoryLoadResult.Success
        assertEquals(2, result.events.size)
        assertEquals(1, result.events.single { !it.isAggregated }.accessCount)
        val older = result.events.single { it.isAggregated }
        assertEquals(6, older.accessCount)
        assertEquals(1, older.rejectCount)
    }

    @Test fun `an interval reaching across the start of individual records keeps the accesses they do not hold`() = runBlocking {
        val repository = repository(at("2026-09-22 12:00:00.000"), """
            Aggregated accesses:
              snapshot:
                begin = 2026-09-14 00:00:00.000
                end = 2026-09-16 00:00:00.000
                Uid u0a166:
                  Package com.example.camera:
                    Attribution null:
                      CAMERA:
                        [top-s] = access=5, reject=1, duration=+9s
            Discrete accesses:
              Uid: 10166
                Package: com.example.camera
                  CAMERA
                    Attribution: null
                      Access [top-s] at 2026-09-15 13:00:00.000
                      Access [bg-s] at 2026-09-15 14:00:00.000
                      Access [top-s] at 2026-09-15 20:00:00.000
        """)
        val result = repository.loadOperationHistory("CAMERA") as AppOpHistoryLoadResult.Success
        val interval = result.events.single { it.isAggregated }
        assertEquals(3, interval.accessCount)
        assertEquals(1, interval.rejectCount)
        assertEquals(null, interval.durationMillis)
    }

    private fun repository(now: Long, output: String) = AppOpsHistoryRepository(
        FakeGateway(output.trimIndent()),
        clock = { now },
    )

    private fun at(time: String): Long =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).parse(time)!!.time

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
