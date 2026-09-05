package dev.izumi.appopsnext.newapps

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.*
import dev.izumi.appopsnext.batch.model.*
import dev.izumi.appopsnext.newapps.model.InstalledPackageFingerprint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NewAppPolicyRuleRunnerTest {
    private val installation = InstalledPackageFingerprint("example.app", 1234)
    private val targets = listOf("CAMERA", "RECORD_AUDIO", "READ_CONTACTS").map {
        BatchOperationTarget("example.app", "测试 | app", 10123, "android:${it.lowercase()}", AppOpScope.PACKAGE, AppOpMode.IGNORE)
    }
    private val success = AppOpModeChangeResult.Success(AppOpMode.DEFAULT, AppOpMode.IGNORE)
    private val unavailable = AppOpModeChangeResult.Failure(AppOpModeChangePhase.READ_ORIGINAL, null, null, AppOpsRestorationStatus.NOT_REQUIRED)

    @Test fun `restart retries unavailable and unattempted rules without replaying success`() = runBlocking {
        val disk = mutableMapOf<String, String>()
        val attempted = mutableListOf<String>()
        val first = NewAppPolicyRuleRunner(
            apply = { attempted += it.stableOperationName; if (it == targets[0]) success else unavailable },
            save = { disk[it.item.target.stableOperationName] = NewAppRuleProgressCodec.encode(it) },
            canContinue = { true },
        ).run(installation, targets, emptyList())
        assertEquals(targets.take(2).map { it.stableOperationName }, attempted)
        assertFalse(first.getValue(targets[0].stableOperationName).retryable)
        assertTrue(first.getValue(targets[1].stableOperationName).retryable)
        attempted.clear()
        val restored = disk.values.mapNotNull(NewAppRuleProgressCodec::decode)
        val second = NewAppPolicyRuleRunner(
            apply = { attempted += it.stableOperationName; success },
            save = {}, canContinue = { true },
        ).run(installation, targets, restored)
        assertEquals(targets.drop(1).map { it.stableOperationName }, attempted)
        assertEquals(3, second.size)
        assertTrue(second.values.none { it.retryable })
    }

    @Test fun `unconfirmed restoration and permanent failure are not automatically replayed`() = runBlocking {
        val unsafe = AppOpModeChangeResult.Failure(AppOpModeChangePhase.RESTORE_ORIGINAL, AppOpMode.DEFAULT, null, AppOpsRestorationStatus.FAILED)
        val previous = listOf(NewAppRuleProgress(installation, BatchOperationItemResult(targets[0], unsafe)))
        val result = NewAppPolicyRuleRunner(apply = { error("Must not write") }, save = {}, canContinue = { true })
            .run(installation, targets.take(1), previous)
        assertEquals(unsafe, result.values.single().item.result)
    }

    @Test fun `changed requested mode invalidates old success`() = runBlocking {
        val previous = listOf(NewAppRuleProgress(installation, BatchOperationItemResult(targets[0], success)))
        val updated = targets[0].copy(requestedMode = AppOpMode.DEFAULT)
        var applied = false
        NewAppPolicyRuleRunner(apply = { applied = true; success }, save = {}, canContinue = { true })
            .run(installation, listOf(updated), previous)
        assertTrue(applied)
    }

    @Test fun `disabled or disconnected runner does not apply rules`() = runBlocking {
        val result = NewAppPolicyRuleRunner(apply = { error("Must not write") }, save = {}, canContinue = { false })
            .run(installation, targets, emptyList())
        assertTrue(result.isEmpty())
    }

    @Test fun `progress codec preserves identity unicode and failure details`() {
        for (result in listOf(success, unavailable)) {
            val record = NewAppRuleProgress(installation, BatchOperationItemResult(targets[0], result))
            assertEquals(record, NewAppRuleProgressCodec.decode(NewAppRuleProgressCodec.encode(record)))
        }
        assertNull(NewAppRuleProgressCodec.decode("invalid"))
    }
}
