package dev.izumi.appopsnext.temporary

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.guard.GuardWriteTarget
import dev.izumi.appopsnext.temporary.TemporaryAllow.Phase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporaryAllowTest {
    private val start = 1_000_000L
    private val entry = TemporaryAllow(
        GuardWriteTarget("com.example", 10123, "CAMERA"), AppOpScope.UID, AppOpMode.IGNORE,
        openByMillis = start + TemporaryAllow.OPEN_WINDOW_MILLIS,
    )

    @Test fun `grant waits for the app and restores after the full grace period`() {
        assertEquals(entry, entry.advance(false, start))
        val active = entry.advance(true, start + 10_000)
        assertEquals(Phase.IN_USE, active.phase)
        val leftAt = entry.openByMillis + 1
        assertEquals(active, active.advance(true, leftAt))
        val away = active.advance(false, leftAt)
        assertEquals(leftAt, away.offScreenSinceMillis)
        assertEquals(away, away.advance(false, leftAt + TemporaryAllow.LEAVE_GRACE_MILLIS - 1))
        assertEquals(active.restoring(), away.advance(false, leftAt + TemporaryAllow.LEAVE_GRACE_MILLIS))
    }

    @Test fun `returning or losing the foreground signal resets the grace period`() {
        val active = entry.advance(true, start)
        val away = active.advance(false, start + 1_000)
        assertEquals(active, away.advance(true, start + 10_000))
        assertEquals(active, away.advance(null, start + 10_000))
        assertEquals(start + 20_000, active.advance(false, start + 20_000).offScreenSinceMillis)
    }

    @Test fun `a grace period recorded before process restart is still honored`() {
        val leftAt = start + 30_000
        val away = entry.advance(true, start).advance(false, leftAt)
        val restored = requireNotNull(TemporaryAllowCodec.decode(TemporaryAllowCodec.encode(away)))
        assertEquals(away, restored)
        assertEquals(Phase.RESTORING, restored.advance(false, leftAt + TemporaryAllow.LEAVE_GRACE_MILLIS).phase)
        assertEquals(leftAt - 1, restored.advance(false, leftAt - 1).offScreenSinceMillis)
    }

    @Test fun `grant expires if the app was not confirmed open in the window`() {
        for (foreground in listOf(false, true, null)) {
            assertEquals(Phase.RESTORING, entry.advance(foreground, entry.openByMillis).phase)
        }
        assertEquals(Phase.RESTORING, entry.advance(false, start - 1).phase)
    }

    @Test fun `persisted recovery retains the original scope and never reopens the window`() {
        for (phase in Phase.entries) {
            val saved = entry.copy(phase = phase)
            assertEquals(saved, TemporaryAllowCodec.decode(TemporaryAllowCodec.encode(saved)))
        }
        val restoring = entry.copy(phase = Phase.RESTORING)
        assertEquals(restoring, restoring.advance(true, start))
        val encoded = TemporaryAllowCodec.encode(entry).split('|')
        for (fieldCount in 7..8) {
            assertEquals(entry, TemporaryAllowCodec.decode(encoded.take(fieldCount).joinToString("|")))
        }
        assertNull(TemporaryAllowCodec.decode("invalid"))
    }

    @Test fun `recovery restores the original mode in the original scope and keeps failures for retry`() = runBlocking {
        val calls = mutableListOf<Pair<AppOpScope, AppOpMode>>()
        val restore: suspend (AppOpScope, AppOpMode) -> Boolean = { scope, mode ->
            calls += scope to mode
            calls.size == 2
        }
        assertFalse(entry.restoreIfStillAllowed(AppOpMode.ALLOW, restore = restore))
        assertTrue(entry.restoreIfStillAllowed(AppOpMode.ALLOW, restore = restore))
        assertEquals(List(2) { entry.scope to entry.originalMode }, calls)
    }

    @Test fun `recovery leaves a newer mode alone and keeps unreadable modes for retry`() = runBlocking {
        val restore: suspend (AppOpScope, AppOpMode) -> Boolean = { _, _ -> error("Unexpected write") }
        assertTrue(entry.restoreIfStillAllowed(AppOpMode.DENY, restore = restore))
        assertTrue(entry.restoreIfStillAllowed(entry.originalMode, restore = restore))
        assertFalse(entry.restoreIfStillAllowed(null, restore = restore))
    }
}
