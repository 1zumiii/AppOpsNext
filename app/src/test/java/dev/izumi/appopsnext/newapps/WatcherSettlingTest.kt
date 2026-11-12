package dev.izumi.appopsnext.newapps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatcherSettlingTest {
    private val settling = WatcherSettling(endWaitMillis = 5_000)

    @Test fun `turning off with no watcher bound leaves the switch free at once`() {
        assertNull(settling.onStopped(hadWatcher = false, nowMillis = 0))
        assertFalse(settling.settling.value)
        assertTrue(settling.mayBind("turned_on"))
    }

    @Test fun `turning off a live watcher holds the switch until its end is seen`() {
        assertEquals(5_000L, settling.onStopped(hadWatcher = true, nowMillis = 1_000))
        assertTrue(settling.settling.value)

        assertNull(settling.onEndSeen())
        assertFalse(settling.settling.value)
        // The upper bound that was scheduled has nothing left to do.
        assertNull(settling.onEndOverdue(6_000))
    }

    @Test fun `an end that never comes frees the switch after the upper bound`() {
        settling.onStopped(hadWatcher = true, nowMillis = 1_000)

        assertEquals(WatcherSettling.Overdue(5_000, null), settling.onEndOverdue(6_000))
        assertFalse(settling.settling.value)
        assertTrue(settling.mayBind("turned_on"))
    }

    @Test fun `a second stop does not cut short the wait for the first one's end`() {
        settling.onStopped(hadWatcher = true, nowMillis = 1_000)
        assertNull(settling.onStopped(hadWatcher = false, nowMillis = 2_000))

        assertTrue(settling.settling.value)
        assertEquals(5_000L, settling.onEndOverdue(6_000)?.waitedMillis)
    }

    @Test fun `turning on holds the switch until the watcher connects or times out`() {
        settling.onStartRequested()
        assertTrue(settling.settling.value)

        settling.onStartSettled()
        assertFalse(settling.settling.value)
    }

    @Test fun `a start asked for before the old watcher ended is deferred until it ends`() {
        settling.onStopped(hadWatcher = true, nowMillis = 1_000)
        settling.onStartRequested()

        assertFalse(settling.mayBind("turned_on"))
        assertTrue(settling.settling.value)

        assertEquals("turned_on", settling.onEndSeen())
        // Still starting: the bind that follows has to connect first.
        assertTrue(settling.settling.value)
        settling.onStartSettled()
        assertFalse(settling.settling.value)
    }

    @Test fun `a deferred start goes ahead at the upper bound`() {
        settling.onStopped(hadWatcher = true, nowMillis = 1_000)
        assertFalse(settling.mayBind("turned_on"))

        assertEquals(WatcherSettling.Overdue(5_000, "turned_on"), settling.onEndOverdue(6_000))
        assertTrue(settling.mayBind("turned_on"))
    }

    @Test fun `turning off again drops a deferred start`() {
        settling.onStopped(hadWatcher = true, nowMillis = 1_000)
        assertFalse(settling.mayBind("turned_on"))
        settling.onStopped(hadWatcher = false, nowMillis = 2_000)

        assertNull(settling.onEndSeen())
    }

    @Test fun `an unexpected loss rebinds without waiting`() {
        // No stop came first, so the rebind after a watcher died on its own
        // binds at once and the switch never waits.
        assertTrue(settling.mayBind("rebind_attempt_1"))
        assertFalse(settling.awaitingEnd)
        assertFalse(settling.settling.value)
        assertNull(settling.onEndSeen())
    }
}
