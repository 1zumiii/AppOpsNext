package dev.izumi.appopsnext.newapps

import org.junit.Assert.assertEquals
import org.junit.Test

class WatcherDisconnectTest {
    @Test fun `a watcher lost while Shizuku stays is a loss of its own`() {
        assertEquals(WatcherDisconnect.UNEXPECTED, WatcherDisconnect.of(bound = true, connected = true, shizukuAlive = true))
    }

    @Test fun `a watcher that connected after its bind timed out is still a loss of its own`() {
        assertEquals(WatcherDisconnect.UNEXPECTED, WatcherDisconnect.of(bound = false, connected = true, shizukuAlive = true))
    }

    @Test fun `a disconnect after turning the watcher off or after Shizuku died is an echo`() {
        // stop() and the Shizuku death listener both leave it unbound and disconnected.
        assertEquals(WatcherDisconnect.ALREADY_ENDED, WatcherDisconnect.of(bound = false, connected = false, shizukuAlive = true))
        assertEquals(WatcherDisconnect.ALREADY_ENDED, WatcherDisconnect.of(bound = false, connected = false, shizukuAlive = false))
    }

    @Test fun `a disconnect before the Shizuku death listener ran is left to that listener`() {
        assertEquals(WatcherDisconnect.SHIZUKU_GONE, WatcherDisconnect.of(bound = true, connected = true, shizukuAlive = false))
    }
}
