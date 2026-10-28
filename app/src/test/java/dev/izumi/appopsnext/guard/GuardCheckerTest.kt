package dev.izumi.appopsnext.guard

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardCheckerTest {
    private val camera = GuardRule("com.example", 10123, "android:camera", AppOpScope.PACKAGE, AppOpMode.IGNORE, autoRestore = false)
    private val audio = camera.copy(stableOperationName = "android:record_audio")
    private var now = 1_000_000L
    private val device = mutableMapOf<String, AppOpMode?>()
    private val restores = mutableListOf<String>()
    private var restoreSucceeds = true

    private val checker = GuardChecker(
        readMode = { device[it.id] },
        restore = { rule ->
            restores += rule.id
            if (restoreSucceeds) device[rule.id] = rule.expectedMode
            restoreSucceeds
        },
        clock = { now },
    )

    @Test fun `a drift is reported once while it stays the same`() = runBlocking {
        device[camera.id] = AppOpMode.ALLOW
        val first = checker.check(listOf(camera), emptyMap())
        assertEquals(listOf(GuardEventKind.DRIFT), first.events.map { it.kind })
        assertEquals(AppOpMode.ALLOW, first.events.single().observedMode)
        val second = checker.check(listOf(camera), first.states)
        assertTrue(second.events.isEmpty())
        device[camera.id] = AppOpMode.FOREGROUND
        assertEquals(listOf(GuardEventKind.DRIFT), checker.check(listOf(camera), second.states).events.map { it.kind })
        assertTrue(restores.isEmpty())
    }

    @Test fun `going back to the expected mode re-arms the report`() = runBlocking {
        device[camera.id] = AppOpMode.ALLOW
        var states = checker.check(listOf(camera), emptyMap()).states
        device[camera.id] = AppOpMode.IGNORE
        states = checker.check(listOf(camera), states).states
        device[camera.id] = AppOpMode.ALLOW
        assertEquals(1, checker.check(listOf(camera), states).events.size)
    }

    @Test fun `automatic restore stops after three attempts in a day and says so once`() = runBlocking {
        val rule = camera.copy(autoRestore = true)
        var states = emptyMap<String, GuardRuleState>()
        val kinds = mutableListOf<GuardEventKind>()
        repeat(5) {
            device[rule.id] = AppOpMode.ALLOW
            val result = checker.check(listOf(rule), states)
            kinds += result.events.map { it.kind }
            states = result.states
            now += 60_000
        }
        assertEquals(
            listOf(GuardEventKind.RESTORED, GuardEventKind.RESTORED, GuardEventKind.RESTORED, GuardEventKind.RESTORE_LIMIT),
            kinds,
        )
        assertEquals(3, restores.size)
        now += GuardRestorePolicy.WINDOW_MILLIS
        assertEquals(listOf(GuardEventKind.RESTORED), checker.check(listOf(rule), states).events.map { it.kind })
    }

    @Test fun `a failed restore is reported and counts toward the limit`() = runBlocking {
        val rule = camera.copy(autoRestore = true)
        restoreSucceeds = false
        device[rule.id] = AppOpMode.ALLOW
        val result = checker.check(listOf(rule), emptyMap())
        assertEquals(listOf(GuardEventKind.RESTORE_FAILED), result.events.map { it.kind })
        assertEquals(1, result.states.getValue(rule.id).restoreTimes.size)
        assertEquals(AppOpMode.ALLOW, result.states.getValue(rule.id).lastObserved)
    }

    @Test fun `an unreadable rule and the rules outside a partial check keep their state`() = runBlocking {
        val kept = GuardRuleState(lastObserved = AppOpMode.ALLOW, restoreTimes = listOf(now - 1))
        val states = mapOf(camera.id to kept, audio.id to kept)
        device[camera.id] = null
        val result = checker.check(listOf(camera), states)
        assertEquals(1, result.readFailures)
        assertEquals(states, result.states)
    }
}
