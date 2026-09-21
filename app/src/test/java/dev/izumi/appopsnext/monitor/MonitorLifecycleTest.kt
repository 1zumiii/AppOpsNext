package dev.izumi.appopsnext.monitor

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class MonitorLifecycleTest {
    @Test fun `recovery stops retrying after success`() = runBlocking {
        var attempts = 0
        val pauses = mutableListOf<Long>()
        val result = retryMonitorRegistration(pause = { pauses += it }) {
            attempts++
            if (attempts == 1) error("service unavailable")
            "registered"
        }
        assertEquals("registered", result.getOrThrow())
        assertEquals(listOf(0L, 1000L), pauses)
    }

    @Test fun `recovery has bounded retries and retains failure`() = runBlocking {
        var attempts = 0
        val failure = IllegalStateException("service unavailable")
        val result = retryMonitorRegistration(pause = {}) { attempts++; throw failure }
        assertSame(failure, result.exceptionOrNull())
        assertEquals(4, attempts)
    }

    @Test fun `cancelled recovery does not retry`() = runBlocking {
        var attempts = 0
        val result = runCatching {
            retryMonitorRegistration(pause = {}) { attempts++; throw CancellationException() }
        }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(1, attempts)
    }

    @Test fun `overlapping starts register only once`() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val lifecycle = MonitorLifecycle<Int>(scope) {}
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var registrations = 0
            val first = async { lifecycle.start { registrations++; entered.complete(Unit); release.await(); 7 } }
            entered.await()
            val second = async { lifecycle.start { registrations++; 8 } }
            yield()
            release.complete(Unit)
            assertEquals(7, first.await())
            assertEquals(7, second.await())
            assertEquals(1, registrations)
            lifecycle.stop {}.join()
        } finally { scope.cancel() }
    }

    @Test fun `stop invalidates in flight registration and closes its result`() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            var registered = false
            val lifecycle = MonitorLifecycle<Int>(scope) { registered = false }
            val entered = CompletableDeferred<MonitorLifecycle.Session>()
            val release = CompletableDeferred<Unit>()
            val start = async {
                runCatching { lifecycle.start { session ->
                    entered.complete(session); release.await(); registered = true; 1
                } }
            }
            val session = entered.await()
            val stopped = lifecycle.stop {}
            assertNull(lifecycle.publish(session) { "stale notification" })
            release.complete(Unit)
            assertTrue(start.await().isFailure)
            stopped.join()
            assertFalse(registered)
            assertNull(lifecycle.session())
        } finally { scope.cancel() }
    }

    @Test fun `clear invalidates queued events but accepts subsequent events`() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val lifecycle = MonitorLifecycle<Int>(scope) {}
            lifecycle.start { 1 }
            val session = lifecycle.session()!!
            val before = lifecycle.revision(session)!!
            lifecycle.clear {}
            assertNull(lifecycle.publish(session, before) { "old event" })
            assertEquals("new event", lifecycle.publish(session, lifecycle.revision(session)) { "new event" })
            lifecycle.stop {}.join()
        } finally { scope.cancel() }
    }

    @Test fun `old stop cleanup cannot unregister a new session`() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            var resource = 0
            val lifecycle = MonitorLifecycle<Int>(scope) { resource = 0 }
            lifecycle.start { resource = 1; 1 }
            val old = lifecycle.session()!!
            val stopped = lifecycle.stop {}
            lifecycle.start { resource = 2; 2 }
            stopped.join()
            assertEquals(2, resource)
            assertNull(lifecycle.publish(old) { true })
            lifecycle.stop {}.join()
        } finally { scope.cancel() }
    }

    @Test fun `recovery keeps the session and cannot publish after stop`() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val lifecycle = MonitorLifecycle<Int>(scope) {}
            lifecycle.start { 1 }
            val session = lifecycle.session()!!
            assertEquals(2, lifecycle.reconnect(session) { 2 })
            assertSame(session, lifecycle.session())
            lifecycle.stop {}.join()
            assertTrue(runCatching { lifecycle.reconnect(session) { fail("must not register"); 3 } }.isFailure)
        } finally { scope.cancel() }
    }
}
