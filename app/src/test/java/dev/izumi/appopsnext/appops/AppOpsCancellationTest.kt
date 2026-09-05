package dev.izumi.appopsnext.appops

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AppOpsCancellationTest {
    @Test
    fun `cancel after package or uid side effect restores original and propagates cancellation`() = runBlocking {
        withTimeout(5_000) {
            for (scope in AppOpScope.entries) {
                val wrote = CompletableDeferred<Unit>()
                val events = mutableListOf<CancelledAppOpsWrite>()
                val gateway = object : Gateway(scope) {
                    override suspend fun afterWrite(mode: AppOpMode) {
                        if (mode == AppOpMode.IGNORE) {
                            wrote.complete(Unit)
                            awaitCancellation()
                        }
                    }
                }
                val repository = AppOpsRepository(gateway, onCancelledWrite = events::add)
                val job = launch { repository.applyMode(PACKAGE, OPERATION, scope, AppOpMode.IGNORE) }
                wrote.await()
                job.cancelAndJoin()
                assertTrue(job.isCancelled)
                assertEquals(AppOpMode.DEFAULT, gateway.mode)
                assertEquals(listOf(AppOpMode.IGNORE, AppOpMode.DEFAULT), gateway.writes)
                assertEquals(scope, events.single().scope)
                assertEquals(AppOpsRestorationStatus.SUCCEEDED, events.single().result.restorationStatus)
            }
        }
    }

    @Test
    fun `cancel during verification restores original before releasing queue`() = runBlocking {
        withTimeout(5_000) {
            val verifying = CompletableDeferred<Unit>()
            val restoring = CompletableDeferred<Unit>()
            val finishRestoration = CompletableDeferred<Unit>()
            val gateway = object : Gateway() {
                override suspend fun beforeRead() {
                    if (mode == AppOpMode.IGNORE) {
                        verifying.complete(Unit)
                        awaitCancellation()
                    }
                }
                override suspend fun beforeWrite(mode: AppOpMode) {
                    if (mode == AppOpMode.DEFAULT) {
                        restoring.complete(Unit)
                        finishRestoration.await()
                    }
                }
            }
            val events = mutableListOf<CancelledAppOpsWrite>()
            val repository = AppOpsRepository(gateway, onCancelledWrite = events::add)
            val a = launch { repository.applyMode(PACKAGE, OPERATION, AppOpScope.PACKAGE, AppOpMode.IGNORE) }
            verifying.await()
            a.cancel()
            restoring.await()
            val b = async(start = CoroutineStart.UNDISPATCHED) {
                AppOpsRepository(gateway).applyMode(PACKAGE, OPERATION, AppOpScope.PACKAGE, AppOpMode.ALLOW)
            }
            assertFalse(b.isCompleted)
            assertEquals(listOf(AppOpMode.IGNORE), gateway.writes)
            finishRestoration.complete(Unit)
            a.join()
            assertTrue(b.await() is AppOpModeChangeResult.Success)
            assertEquals(listOf(AppOpMode.IGNORE, AppOpMode.DEFAULT, AppOpMode.ALLOW), gateway.writes)
            assertEquals(AppOpsRestorationStatus.SUCCEEDED, events.single().result.restorationStatus)
        }
    }

    @Test
    fun `cancel during original read performs no writes and does not return a backend failure`() = runBlocking {
        withTimeout(5_000) {
            val reading = CompletableDeferred<Unit>()
            val events = mutableListOf<CancelledAppOpsWrite>()
            val gateway = object : Gateway() {
                override suspend fun beforeRead() {
                    reading.complete(Unit)
                    awaitCancellation()
                }
            }
            var returned = false
            val repository = AppOpsRepository(gateway, onCancelledWrite = events::add)
            val job = launch {
                repository.applyMode(PACKAGE, OPERATION, AppOpScope.PACKAGE, AppOpMode.IGNORE)
                returned = true
            }
            reading.await()
            job.cancelAndJoin()
            assertFalse(returned)
            assertTrue(gateway.writes.isEmpty())
            assertTrue(events.isEmpty())
        }
    }

    @Test
    fun `failed cleanup remains observable after caller cancellation`() = runBlocking {
        withTimeout(5_000) {
            val wrote = CompletableDeferred<Unit>()
            val events = mutableListOf<CancelledAppOpsWrite>()
            val gateway = object : Gateway() {
                override suspend fun beforeWrite(mode: AppOpMode) {
                    if (mode == AppOpMode.DEFAULT) error("backend disconnected")
                }
                override suspend fun afterWrite(mode: AppOpMode) {
                    wrote.complete(Unit)
                    awaitCancellation()
                }
            }
            val repository = AppOpsRepository(gateway, onCancelledWrite = events::add)
            val job = launch { repository.applyMode(PACKAGE, OPERATION, AppOpScope.PACKAGE, AppOpMode.IGNORE) }
            wrote.await()
            job.cancelAndJoin()
            assertEquals(AppOpMode.IGNORE, gateway.mode)
            assertEquals(AppOpsRestorationStatus.FAILED, events.single().result.restorationStatus)
            assertTrue(job.isCancelled)
        }
    }

    @Test
    fun `unresponsive cooperative cleanup times out and releases transaction queue`() = runBlocking {
        withTimeout(5_000) {
            val wrote = CompletableDeferred<Unit>()
            val events = mutableListOf<CancelledAppOpsWrite>()
            val gateway = object : Gateway() {
                override suspend fun beforeWrite(mode: AppOpMode) {
                    if (mode == AppOpMode.DEFAULT) awaitCancellation()
                }
                override suspend fun afterWrite(mode: AppOpMode) {
                    if (mode == AppOpMode.IGNORE) {
                        wrote.complete(Unit)
                        awaitCancellation()
                    }
                }
            }
            val repository = AppOpsRepository(
                gateway,
                cancellationRecoveryTimeoutMillis = 50,
                onCancelledWrite = events::add,
            )
            val job = launch { repository.applyMode(PACKAGE, OPERATION, AppOpScope.PACKAGE, AppOpMode.IGNORE) }
            wrote.await()
            job.cancelAndJoin()
            assertEquals(AppOpsRestorationStatus.FAILED, events.single().result.restorationStatus)
            assertTrue(repository.applyMode(PACKAGE, OPERATION, AppOpScope.PACKAGE, AppOpMode.ALLOW) is AppOpModeChangeResult.Success)
            assertEquals(AppOpMode.ALLOW, gateway.mode)
        }
    }

    private open class Gateway(private val scope: AppOpScope = AppOpScope.PACKAGE) : PrivilegedAppOpsGateway {
        var mode = AppOpMode.DEFAULT
        val writes = mutableListOf<AppOpMode>()
        open suspend fun beforeRead() {}
        open suspend fun beforeWrite(mode: AppOpMode) {}
        open suspend fun afterWrite(mode: AppOpMode) {}
        override suspend fun getPackageOps(packageName: String) = error("unused")
        override suspend fun getPackageOp(packageName: String, operationName: String): ShellCommandResult = withContext(Dispatchers.IO) {
            beforeRead()
            val prefix = if (scope == AppOpScope.UID) "Uid mode: " else ""
            ShellCommandResult(0, "${prefix}RUN_IN_BACKGROUND: ${mode.shellValue}", "", false)
        }
        override suspend fun setPackageOpMode(packageName: String, operationName: String, mode: AppOpMode): ShellCommandResult {
            assertEquals(AppOpScope.PACKAGE, scope)
            return write(mode)
        }
        override suspend fun setUidOpMode(packageName: String, operationName: String, mode: AppOpMode): ShellCommandResult {
            assertEquals(AppOpScope.UID, scope)
            return write(mode)
        }
        private suspend fun write(mode: AppOpMode): ShellCommandResult = withContext(Dispatchers.IO) {
            beforeWrite(mode)
            this@Gateway.mode = mode
            writes += mode
            afterWrite(mode)
            ShellCommandResult(0, "", "", false)
        }
    }

    private companion object {
        const val PACKAGE = "com.example.target"
        val OPERATION = AppOpIdentifier("android:run_in_background", "RUN_IN_BACKGROUND")
    }
}
