package dev.izumi.appopsnext.guard

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardUserWritesTest {
    private val checkMutex = Mutex()
    private val writes = GuardUserWrites(checkMutex)
    private val camera = GuardRule("com.example", 10123, "android:camera", AppOpScope.PACKAGE, AppOpMode.IGNORE, autoRestore = true)
    private val sibling = camera.copy(packageName = "com.sibling", scope = AppOpScope.UID)
    private val audio = camera.copy(stableOperationName = "android:record_audio")
    private val target = GuardWriteTarget(camera.packageName, camera.uid, "CAMERA")

    @Test fun `slow writes suppress drift until the new rule is saved`() = runBlocking {
        var rules = listOf(camera, sibling, audio)
        val modes = mutableMapOf(camera.id to AppOpMode.IGNORE, sibling.id to AppOpMode.IGNORE, audio.id to AppOpMode.ALLOW)
        val reads = mutableListOf<GuardRule>()
        val rechecks = mutableListOf<Set<String>>()
        val checker = GuardChecker(
            readMode = { rule ->
                reads += rule
                modes.getValue(rule.id)
            },
            restore = { rule ->
                assertEquals(audio, rule)
                modes[rule.id] = rule.expectedMode
                true
            },
        )
        val check: suspend () -> GuardChecker.Result = {
            checkMutex.withLock { checker.check(rules.filterNot(writes::shouldSkip), emptyMap()) }
        }
        val deviceChanged = CompletableDeferred<Unit>()
        val saveRule = CompletableDeferred<Unit>()
        val write = launch {
            writes.run(listOf(target), rechecks::add) {
                modes[camera.id] = AppOpMode.ALLOW
                deviceChanged.complete(Unit)
                saveRule.await()
                rules = rules.map { if (it == camera) it.copy(expectedMode = AppOpMode.ALLOW) else it }
            }
        }
        deviceChanged.await()
        val during = check()
        assertEquals(listOf(audio), reads)
        assertEquals(listOf(GuardEventKind.RESTORED), during.events.map { it.kind })
        saveRule.complete(Unit)
        write.join()

        assertEquals(listOf(setOf(camera.stableOperationName)), rechecks)
        assertTrue(check().events.isEmpty())
        assertEquals(listOf(camera.copy(expectedMode = AppOpMode.ALLOW), sibling, audio), reads.drop(1))
    }

    @Test fun `a check already holding a rule snapshot finishes before the user writes`() = runBlocking {
        var started = false
        val write = checkMutex.withLock {
            launch(start = CoroutineStart.UNDISPATCHED) {
                writes.run(listOf(target), {}) { started = true }
            }.also { assertFalse(started) }
        }
        write.join()
        assertTrue(started)
    }

    @Test fun `cancelling a batch releases all of its targets and rechecks what was skipped`() = runBlocking {
        val rechecks = mutableListOf<Set<String>>()
        val audioTarget = GuardWriteTarget(audio.packageName, audio.uid, audio.stableOperationName)
        val batch = launch(start = CoroutineStart.UNDISPATCHED) {
            writes.run(listOf(target, audioTarget), rechecks::add) { awaitCancellation() }
        }
        checkMutex.withLock {
            assertTrue(writes.shouldSkip(camera))
            assertTrue(writes.shouldSkip(audio))
            assertFalse(writes.shouldSkip(camera.copy(packageName = "com.other", uid = 10124)))
        }
        batch.cancelAndJoin()
        checkMutex.withLock {
            assertFalse(writes.shouldSkip(camera))
            assertFalse(writes.shouldSkip(audio))
        }
        assertEquals(listOf(setOf(camera.stableOperationName, audio.stableOperationName)), rechecks)
    }
}
