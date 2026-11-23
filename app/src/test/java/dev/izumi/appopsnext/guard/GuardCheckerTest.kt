package dev.izumi.appopsnext.guard

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardCheckerTest {
    private val camera = GuardRule("com.example", 10123, "android:camera", AppOpScope.PACKAGE, AppOpMode.IGNORE, autoRestore = false)
    private val audio = camera.copy(stableOperationName = "android:record_audio")
    private var now = 1_000_000L
    private val device = mutableMapOf<String, AppOpMode?>()
    private val restores = mutableListOf<String>()
    private var restoreSucceeds = true
    private var onScreen = false
    private val screenQueries = mutableListOf<String>()

    private val checker = GuardChecker(
        readMode = { device[it.id] },
        restore = { rule ->
            restores += rule.id
            if (restoreSucceeds) device[rule.id] = rule.expectedMode
            restoreSucceeds
        },
        isOnScreen = { rule ->
            screenQueries += rule.id
            onScreen
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

    @Test fun `the restore limit follows the setting`() = runBlocking {
        val rule = camera.copy(autoRestore = true)
        var states = emptyMap<String, GuardRuleState>()
        val kinds = mutableListOf<GuardEventKind>()
        repeat(3) {
            device[rule.id] = AppOpMode.ALLOW
            val result = checker.check(listOf(rule), states, restoreLimit = 1)
            kinds += result.events.map { it.kind }
            states = result.states
            now += 60_000
        }
        assertEquals(listOf(GuardEventKind.RESTORED, GuardEventKind.RESTORE_LIMIT), kinds)
        // A limit outside the range is clamped rather than turning restores off.
        assertEquals(
            listOf(GuardEventKind.RESTORED),
            checker.check(listOf(rule), emptyMap(), restoreLimit = 0).events.map { it.kind },
        )
    }

    @Test fun `a delayed restore waits for the delay, reported once, then restores`() = runBlocking {
        val rule = camera.copy(autoRestore = true, restoreDelayMinutes = 10)
        device[rule.id] = AppOpMode.ALLOW
        val started = now
        val first = checker.check(listOf(rule), emptyMap())
        assertEquals(listOf(GuardEventKind.RESTORE_PENDING), first.events.map { it.kind })
        assertEquals(started, first.states.getValue(rule.id).driftSince)
        assertEquals(started + 600_000, GuardRestorePolicy.nextDue(listOf(rule), first.states, now, 3))

        now += 300_000
        device[rule.id] = AppOpMode.FOREGROUND
        val second = checker.check(listOf(rule), first.states)
        // Another mode during the wait is a new report, and the wait still counts from the first.
        assertEquals(listOf(GuardEventKind.RESTORE_PENDING), second.events.map { it.kind })
        assertEquals(started, second.states.getValue(rule.id).driftSince)
        assertTrue(restores.isEmpty())

        now = started + 600_000
        val third = checker.check(listOf(rule), second.states)
        assertEquals(listOf(GuardEventKind.RESTORED), third.events.map { it.kind })
        assertEquals(null, third.states.getValue(rule.id).driftSince)
        assertEquals(null, GuardRestorePolicy.nextDue(listOf(rule), third.states, now, 3))

        // The next change waits again from when it is seen.
        now += 60_000
        device[rule.id] = AppOpMode.ALLOW
        val fourth = checker.check(listOf(rule), third.states)
        assertEquals(listOf(GuardEventKind.RESTORE_PENDING), fourth.events.map { it.kind })
        assertEquals(now, fourth.states.getValue(rule.id).driftSince)
    }

    @Test fun `a change undone during the delay is not restored`() = runBlocking {
        val rule = camera.copy(autoRestore = true, restoreDelayMinutes = 5)
        device[rule.id] = AppOpMode.ALLOW
        val first = checker.check(listOf(rule), emptyMap())
        device[rule.id] = AppOpMode.IGNORE
        now += 60_000
        val second = checker.check(listOf(rule), first.states)
        assertTrue(second.events.isEmpty())
        assertEquals(null, second.states.getValue(rule.id).driftSince)
        assertEquals(null, GuardRestorePolicy.nextDue(listOf(rule), second.states, now, 3))
        assertTrue(restores.isEmpty())
    }

    @Test fun `no delayed check is due for a rule past its delay, at its limit or without restore`() {
        val delayed = camera.copy(autoRestore = true, restoreDelayMinutes = 1)
        val waiting = GuardRuleState(lastObserved = AppOpMode.ALLOW, driftSince = now)
        assertEquals(now + 60_000, GuardRestorePolicy.nextDue(listOf(delayed), mapOf(delayed.id to waiting), now, 3))
        // A failed restore past its delay is left to the next regular check.
        assertEquals(null, GuardRestorePolicy.nextDue(listOf(delayed), mapOf(delayed.id to waiting), now + 60_000, 3))
        val limited = waiting.copy(restoreTimes = listOf(now - 1))
        assertEquals(null, GuardRestorePolicy.nextDue(listOf(delayed), mapOf(delayed.id to limited), now, 1))
        val notifying = delayed.copy(autoRestore = false)
        assertEquals(null, GuardRestorePolicy.nextDue(listOf(notifying), mapOf(delayed.id to waiting), now, 3))
    }

    @Test fun `a rule that waits for the app to leave holds while it is on screen`() = runBlocking {
        val rule = camera.copy(autoRestore = true, restoreAfterLeaving = true)
        onScreen = true
        device[rule.id] = AppOpMode.ALLOW
        val first = checker.check(listOf(rule), emptyMap())
        assertEquals(listOf(GuardEventKind.RESTORE_PENDING), first.events.map { it.kind })
        assertTrue(first.states.getValue(rule.id).heldOnScreen)
        assertEquals(
            now + GuardRestorePolicy.ON_SCREEN_RECHECK_MILLIS,
            GuardRestorePolicy.nextDue(listOf(rule), first.states, now, 3),
        )

        now += GuardRestorePolicy.ON_SCREEN_RECHECK_MILLIS
        val second = checker.check(listOf(rule), first.states)
        assertTrue(second.events.isEmpty())
        assertTrue(restores.isEmpty())

        onScreen = false
        now += GuardRestorePolicy.ON_SCREEN_RECHECK_MILLIS
        val third = checker.check(listOf(rule), second.states)
        assertEquals(listOf(GuardEventKind.RESTORED), third.events.map { it.kind })
        assertFalse(third.states.getValue(rule.id).heldOnScreen)
        assertEquals(null, GuardRestorePolicy.nextDue(listOf(rule), third.states, now, 3))
    }

    @Test fun `a change made while the app is away is restored at once`() = runBlocking {
        val rule = camera.copy(autoRestore = true, restoreAfterLeaving = true)
        device[rule.id] = AppOpMode.ALLOW
        assertEquals(listOf(GuardEventKind.RESTORED), checker.check(listOf(rule), emptyMap()).events.map { it.kind })
    }

    @Test fun `with a delay too, the screen is asked only once the delay has passed`() = runBlocking {
        val rule = camera.copy(autoRestore = true, restoreDelayMinutes = 1, restoreAfterLeaving = true)
        onScreen = true
        device[rule.id] = AppOpMode.ALLOW
        val first = checker.check(listOf(rule), emptyMap())
        assertEquals(listOf(GuardEventKind.RESTORE_PENDING), first.events.map { it.kind })
        assertTrue(screenQueries.isEmpty())
        assertFalse(first.states.getValue(rule.id).heldOnScreen)

        now += 60_000
        val second = checker.check(listOf(rule), first.states)
        assertTrue(second.events.isEmpty())
        assertEquals(listOf(rule.id), screenQueries)
        assertTrue(second.states.getValue(rule.id).heldOnScreen)

        onScreen = false
        now += GuardRestorePolicy.ON_SCREEN_RECHECK_MILLIS
        assertEquals(listOf(GuardEventKind.RESTORED), checker.check(listOf(rule), second.states).events.map { it.kind })
    }

    @Test fun `the screen is not asked for a rule that does not wait for the app`() = runBlocking {
        onScreen = true
        device[camera.id] = AppOpMode.ALLOW
        checker.check(listOf(camera.copy(autoRestore = true)), emptyMap())
        checker.check(listOf(camera.copy(restoreAfterLeaving = true)), emptyMap())
        assertTrue(screenQueries.isEmpty())
    }

    @Test fun `a delay of zero restores at once`() = runBlocking {
        val rule = camera.copy(autoRestore = true, restoreDelayMinutes = 0)
        device[rule.id] = AppOpMode.ALLOW
        assertEquals(listOf(GuardEventKind.RESTORED), checker.check(listOf(rule), emptyMap()).events.map { it.kind })
    }

    @Test fun `a setting counts as guarded in either scope and either spelling`() {
        val rules = listOf(camera.copy(scope = AppOpScope.UID))
        assertTrue(rules.guards("com.example", "CAMERA"))
        assertTrue(rules.guards("com.example", "android:camera"))
        assertFalse(rules.guards("com.example", "android:record_audio"))
        assertFalse(rules.guards("com.other", "CAMERA"))
    }
}
