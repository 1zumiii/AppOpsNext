package dev.izumi.appopsnext.appops

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpIdentifier
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.appops.model.ShellCommandResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOpsWriteTransactionTest {
    @Test
    fun `repositories share a queue until failed verification and restoration finish`() = runBlocking {
        withTimeout(5_000) {
            val verifying = CompletableDeferred<Unit>()
            val continueVerification = CompletableDeferred<Unit>()
            val gateway = object : StatefulGateway() {
                override suspend fun getPackageOp(packageName: String, operationName: String): ShellCommandResult {
                    if (mode == AppOpMode.IGNORE) {
                        verifying.complete(Unit)
                        continueVerification.await()
                        return ShellCommandResult(1, "", "read failed", false)
                    }
                    return super.getPackageOp(packageName, operationName)
                }
            }
            val first = AppOpsRepository(gateway)
            val second = AppOpsRepository(gateway)
            val a = async { first.applyMode(PACKAGE, OPERATION, AppOpScope.PACKAGE, AppOpMode.IGNORE) }
            verifying.await()
            val b = async(start = CoroutineStart.UNDISPATCHED) {
                second.applyMode(PACKAGE, OPERATION, AppOpScope.PACKAGE, AppOpMode.ALLOW)
            }
            assertFalse(b.isCompleted)
            assertEquals(listOf(AppOpMode.IGNORE), gateway.writes)
            continueVerification.complete(Unit)
            assertTrue(a.await() is AppOpModeChangeResult.Failure)
            assertTrue(b.await() is AppOpModeChangeResult.Success)
            assertEquals(AppOpMode.ALLOW, gateway.mode)
            assertEquals(listOf(AppOpMode.IGNORE, AppOpMode.DEFAULT, AppOpMode.ALLOW), gateway.writes)
        }
    }

    @Test
    fun `outer transaction holds queue between fallback attempts`() = runBlocking {
        withTimeout(5_000) {
            val firstFinished = CompletableDeferred<Unit>()
            val retryAllowed = CompletableDeferred<Unit>()
            val gateway = StatefulGateway()
            val repository = AppOpsRepository(gateway)
            val otherRepository = AppOpsRepository(gateway)
            val a = async {
                repository.withWriteTransaction { transaction ->
                    transaction.applyMode(PACKAGE, OPERATION, AppOpScope.PACKAGE, AppOpMode.IGNORE)
                    firstFinished.complete(Unit)
                    retryAllowed.await()
                    transaction.applyMode(PACKAGE, OPERATION, AppOpScope.PACKAGE, AppOpMode.DENY)
                }
            }
            firstFinished.await()
            val b = async(start = CoroutineStart.UNDISPATCHED) {
                otherRepository.applyMode(PACKAGE, OPERATION, AppOpScope.PACKAGE, AppOpMode.ALLOW)
            }
            assertFalse(b.isCompleted)
            assertEquals(listOf(AppOpMode.IGNORE), gateway.writes)
            retryAllowed.complete(Unit)
            a.await()
            b.await()
            assertEquals(listOf(AppOpMode.IGNORE, AppOpMode.DENY, AppOpMode.ALLOW), gateway.writes)
        }
    }

    @Test
    fun `cancelled queued request never reaches backend`() = runBlocking {
        withTimeout(5_000) {
            val gateway = StatefulGateway()
            val repository = AppOpsRepository(gateway)
            repository.withWriteTransaction {
                val waiting = async(start = CoroutineStart.UNDISPATCHED) {
                    repository.applyMode(PACKAGE, OPERATION, AppOpScope.PACKAGE, AppOpMode.IGNORE)
                }
                waiting.cancel()
                waiting.join()
            }
            assertTrue(gateway.writes.isEmpty())
            assertEquals(0, gateway.reads)
        }
    }

    private open class StatefulGateway : PrivilegedAppOpsGateway {
        var mode = AppOpMode.DEFAULT
        var reads = 0
        val writes = mutableListOf<AppOpMode>()
        override suspend fun getPackageOps(packageName: String) = error("unused")
        override suspend fun getPackageOp(packageName: String, operationName: String): ShellCommandResult {
            reads++
            return ShellCommandResult(0, "RUN_IN_BACKGROUND: ${mode.shellValue}", "", false)
        }
        override suspend fun setPackageOpMode(packageName: String, operationName: String, mode: AppOpMode): ShellCommandResult {
            this.mode = mode
            writes += mode
            return ShellCommandResult(0, "", "", false)
        }
        override suspend fun setUidOpMode(packageName: String, operationName: String, mode: AppOpMode) = error("unused")
    }

    private companion object {
        const val PACKAGE = "com.example.target"
        val OPERATION = AppOpIdentifier("android:run_in_background", "RUN_IN_BACKGROUND")
    }
}
