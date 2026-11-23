package dev.izumi.appopsnext.temporary

import dev.izumi.appopsnext.appops.AppOpsRepository
import dev.izumi.appopsnext.appops.PrivilegedAppOpsGateway
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpIdentifier
import dev.izumi.appopsnext.appops.model.AppOpModeChangePhase
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.appops.model.ShellCommandResult
import dev.izumi.appopsnext.guard.GuardWriteTarget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporaryAllowWriterTest {
    private val target = GuardWriteTarget("com.example", 10123, "POST_NOTIFICATION")
    private val operation = AppOpIdentifier(target.stableOperationName, "POST_NOTIFICATION")
    private val gateway = NotificationGateway()
    private val repository = AppOpsRepository(gateway)
    private var recovery: TemporaryAllow? = null

    @Test fun `notification permission retries on a private UID and restores that UID slot`() = runBlocking {
        val result = allow(listOf(target.packageName)) as TemporaryAllowResult.Write
        assertTrue(result.result is AppOpModeChangeResult.Success)
        assertEquals(AppOpScope.UID, result.scope)
        val entry = requireNotNull(recovery)
        assertEquals(AppOpScope.UID, entry.scope)
        assertEquals(AppOpMode.DEFAULT, entry.originalMode)
        assertEquals(AppOpMode.IGNORE, entry.displayedOriginalMode)
        assertEquals(entry, TemporaryAllowCodec.decode(TemporaryAllowCodec.encode(entry)))
        assertEquals(AppOpMode.IGNORE, read(AppOpScope.PACKAGE))
        assertEquals(AppOpMode.ALLOW, read(AppOpScope.UID))

        val completed = repository.withWriteTransaction { transaction ->
            entry.restoreIfStillAllowed(read(entry.scope)) { scope, original ->
                transaction.changeMode(
                    target.packageName, operation, scope, AppOpMode.ALLOW, original,
                    requireExactOriginalMode = true,
                ) is AppOpModeChangeResult.Success
            }
        }
        assertTrue(completed)
        assertEquals(AppOpMode.DEFAULT, read(AppOpScope.UID))
        assertEquals(AppOpMode.IGNORE, read(AppOpScope.PACKAGE))
    }

    @Test fun `notification permission leaves a shared UID untouched after package rejection`() = runBlocking {
        val result = allow(listOf(target.packageName, "com.sibling")) as TemporaryAllowResult.Write
        val failure = result.result as AppOpModeChangeResult.Failure
        assertEquals(AppOpModeChangePhase.VERIFY_REQUESTED, failure.phase)
        assertEquals(AppOpMode.DEFAULT, failure.observedMode)
        assertEquals(AppOpScope.PACKAGE, result.scope)
        assertEquals(AppOpMode.IGNORE, read(AppOpScope.PACKAGE))
        assertEquals(0, gateway.uidWrites)
        assertNull(recovery)
    }

    @Test fun `an explicit package Allow lets temporary notification access clear and restore the UID override`() = runBlocking {
        gateway.packageMode = AppOpMode.ALLOW
        gateway.uidMode = AppOpMode.IGNORE
        val result = allow(listOf(target.packageName), AppOpScope.UID) as TemporaryAllowResult.Write
        assertEquals(AppOpModeChangeResult.Success(AppOpMode.IGNORE, AppOpMode.DEFAULT), result.result)
        val entry = requireNotNull(recovery)
        assertEquals(AppOpMode.DEFAULT, entry.appliedMode)
        assertEquals(AppOpMode.IGNORE, entry.originalMode)
        assertEquals(entry, TemporaryAllowCodec.decode(TemporaryAllowCodec.encode(entry)))
        assertEquals(AppOpMode.ALLOW, read(AppOpScope.PACKAGE))

        val completed = repository.withWriteTransaction { transaction ->
            entry.restoreIfStillAllowed(read(entry.scope), { read(AppOpScope.PACKAGE) }) { scope, original ->
                transaction.changeMode(
                    target.packageName, operation, scope, entry.appliedMode, original,
                    requireExactOriginalMode = true,
                ) is AppOpModeChangeResult.Success
            }
        }
        assertTrue(completed)
        assertEquals(AppOpMode.IGNORE, read(AppOpScope.UID))
        assertEquals(AppOpMode.ALLOW, read(AppOpScope.PACKAGE))
    }

    @Test fun `recovery preserves a newer package change after clearing a UID override`() = runBlocking {
        gateway.packageMode = AppOpMode.ALLOW
        gateway.uidMode = AppOpMode.IGNORE
        allow(listOf(target.packageName), AppOpScope.UID)
        val entry = requireNotNull(recovery)
        gateway.packageMode = AppOpMode.IGNORE
        assertTrue(entry.restoreIfStillAllowed(read(entry.scope), { read(AppOpScope.PACKAGE) }) { _, _ ->
            error("A newer package mode must stay in effect")
        })
        assertEquals(AppOpMode.DEFAULT, read(AppOpScope.UID))
    }

    private suspend fun allow(
        packages: List<String>,
        preferredScope: AppOpScope = AppOpScope.PACKAGE,
    ): TemporaryAllowResult =
        repository.withWriteTransaction { transaction ->
            TemporaryAllowWriter(packagesForUid = { packages }).allow(
                target, preferredScope, AppOpMode.IGNORE,
                readMode = ::read,
                stage = {
                    assertNull(recovery)
                    recovery = it
                    true
                },
                clear = {
                    assertEquals(it, recovery)
                    recovery = null
                },
                save = { recovery = it },
            ) { scope, original, acceptUidDefault ->
                assertEquals(scope, recovery?.scope)
                transaction.changeMode(
                    target.packageName, operation, scope, original, AppOpMode.ALLOW,
                    requireExactOriginalMode = true,
                    acceptUidDefaultForAllow = acceptUidDefault,
                )
            }
        }

    private suspend fun read(scope: AppOpScope): AppOpMode? =
        repository.readMode(target.packageName, operation, scope)

    private class NotificationGateway : PrivilegedAppOpsGateway {
        var uidWrites = 0
            private set
        var packageMode = AppOpMode.IGNORE
        var uidMode = AppOpMode.DEFAULT

        override suspend fun getPackageOps(packageName: String): ShellCommandResult = error("Unused")

        override suspend fun getPackageOp(packageName: String, operationName: String) = success(
            "Uid mode: POST_NOTIFICATION: ${uidMode.shellValue}\nPOST_NOTIFICATION: ${packageMode.shellValue}",
        )

        override suspend fun setPackageOpMode(packageName: String, operationName: String, mode: AppOpMode): ShellCommandResult {
            packageMode = if (mode == AppOpMode.ALLOW) AppOpMode.DEFAULT else mode
            return success()
        }

        override suspend fun setUidOpMode(packageName: String, operationName: String, mode: AppOpMode): ShellCommandResult {
            uidWrites++
            uidMode = if (mode == AppOpMode.ALLOW && packageMode == AppOpMode.ALLOW) AppOpMode.DEFAULT else mode
            return success()
        }

        private fun success(output: String = "") = ShellCommandResult(0, output, "", timedOut = false)
    }
}
