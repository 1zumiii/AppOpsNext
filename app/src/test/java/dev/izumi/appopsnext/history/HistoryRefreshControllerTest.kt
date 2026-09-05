package dev.izumi.appopsnext.history

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.*
import org.junit.Test

class HistoryRefreshControllerTest {
    @Test fun `hidden background and disconnected states stop and cancel refreshes`() = runBlocking {
        val owner = Job()
        val scope = CoroutineScope(coroutineContext + owner)
        val started = Channel<Unit>(Channel.UNLIMITED)
        val cancelled = Channel<Unit>(Channel.UNLIMITED)
        val controller = HistoryRefreshController(scope, 20) {
            started.send(Unit)
            try { awaitCancellation() } finally { cancelled.trySend(Unit) }
        }
        try {
            controller.setConnected(true)
            controller.setForeground(true)
            controller.requestRefresh()
            assertNull(withTimeoutOrNull(100) { started.receive() })
            controller.setVisible(true)
            withTimeout(2_000) { started.receive() }
            controller.setVisible(false)
            withTimeout(2_000) { cancelled.receive() }
            assertNull(withTimeoutOrNull(100) { started.receive() })
            controller.setVisible(true)
            withTimeout(2_000) { started.receive() }
            controller.setForeground(false)
            withTimeout(2_000) { cancelled.receive() }
            assertNull(withTimeoutOrNull(100) { started.receive() })
            controller.setForeground(true)
            withTimeout(2_000) { started.receive() }
            controller.setConnected(false)
            withTimeout(2_000) { cancelled.receive() }
            assertNull(withTimeoutOrNull(100) { started.receive() })
        } finally { owner.cancelAndJoin() }
    }

    @Test fun `rapid refresh requests coalesce into one follow up without concurrency`() = runBlocking {
        val owner = Job()
        val scope = CoroutineScope(coroutineContext + owner)
        val started = Channel<Int>(Channel.UNLIMITED)
        val finishFirst = CompletableDeferred<Unit>()
        var count = 0
        val controller = HistoryRefreshController(scope, 60_000) {
            val current = ++count
            started.send(current)
            if (current == 1) finishFirst.await() else awaitCancellation()
        }
        try {
            controller.setVisible(true)
            controller.setForeground(true)
            controller.setConnected(true)
            assertEquals(1, withTimeout(2_000) { started.receive() })
            repeat(100) { controller.requestRefresh() }
            assertEquals(1, count)
            finishFirst.complete(Unit)
            assertEquals(2, withTimeout(2_000) { started.receive() })
            assertNull(withTimeoutOrNull(100) { started.receive() })
        } finally { owner.cancelAndJoin() }
    }
}
