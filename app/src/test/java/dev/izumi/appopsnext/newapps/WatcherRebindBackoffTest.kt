package dev.izumi.appopsnext.newapps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatcherRebindBackoffTest {
    private fun backoff() = WatcherRebindBackoff(delaysMillis = listOf(1L, 2L), stableMillis = 100L)

    @Test
    fun stopsAfterTheLastDelay() {
        val backoff = backoff()

        assertEquals(1L, backoff.nextDelayMillis(0))
        assertEquals(2L, backoff.nextDelayMillis(0))
        assertNull(backoff.nextDelayMillis(0))
    }

    @Test
    fun aWatcherThatDiesQuicklyKeepsCounting() {
        val backoff = backoff()

        assertEquals(1L, backoff.nextDelayMillis(0))
        backoff.onConnected(10)
        assertEquals(2L, backoff.nextDelayMillis(50))
        backoff.onConnected(60)
        assertNull(backoff.nextDelayMillis(70))
    }

    @Test
    fun aWatcherThatRanLongEnoughStartsOver() {
        val backoff = backoff()

        assertEquals(1L, backoff.nextDelayMillis(0))
        assertEquals(2L, backoff.nextDelayMillis(0))
        backoff.onConnected(10)
        assertEquals(1L, backoff.nextDelayMillis(110))
    }

    @Test
    fun aConnectionCountsOnce() {
        val backoff = backoff()

        backoff.onConnected(0)
        assertEquals(1L, backoff.nextDelayMillis(200))
        // No connection since: the long run before must not reset the count again.
        assertEquals(2L, backoff.nextDelayMillis(400))
    }

    @Test
    fun resetStartsOver() {
        val backoff = backoff()

        backoff.nextDelayMillis(0)
        backoff.nextDelayMillis(0)
        backoff.reset()

        assertEquals(1L, backoff.nextDelayMillis(0))
    }
}
