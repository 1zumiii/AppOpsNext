package dev.izumi.appopsnext.nativebackend

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class NativeDaemonChannelTest {
    @Test fun `close does not wait for blocked read or blocked cleanup`() = runBlocking {
        val reading = CountDownLatch(1)
        val release = CountDownLatch(1)
        val aborted = CountDownLatch(1)
        val abortCount = AtomicInteger()
        val channel = NativeDaemonChannel(
            exchange = { reading.countDown(); awaitUninterruptibly(release); "response" },
            abort = { abortCount.incrementAndGet(); aborted.countDown(); awaitUninterruptibly(release) },
        )
        try {
            val request = async { runCatching { channel.request("request") } }
            withContext(Dispatchers.IO) { assertTrue(reading.await(2, TimeUnit.SECONDS)) }
            withTimeout(1_000) { withContext(Dispatchers.Default) { channel.close(); channel.close() } }
            withContext(Dispatchers.IO) { assertTrue(aborted.await(2, TimeUnit.SECONDS)) }
            assertTrue(withTimeout(2_000) { request.await() }.exceptionOrNull() is IOException)
            assertEquals(1, abortCount.get())
        } finally { release.countDown(); channel.close() }
    }

    @Test fun `client deadline expires even when remote pipe ignores interruption`() = runBlocking {
        val release = CountDownLatch(1)
        val channel = NativeDaemonChannel(
            exchange = { awaitUninterruptibly(release); "late response" },
            abort = {},
            timeoutMillis = 50,
        )
        try {
            val result = withTimeout(2_000) { runCatching { channel.request("request") } }
            assertTrue(result.exceptionOrNull() is IOException)
            assertTrue(runCatching { channel.request("next") }.isFailure)
        } finally { release.countDown(); channel.close() }
    }

    @Test fun `requests retain their response ordering`() = runBlocking {
        val channel = NativeDaemonChannel(exchange = { "reply:$it" }, abort = {})
        try {
            val results = (1..20).map { n -> async { channel.request(n.toString()) } }.awaitAll()
            assertEquals((1..20).map { "reply:$it" }, results)
        } finally { channel.close() }
    }

    private fun awaitUninterruptibly(latch: CountDownLatch) {
        while (true) {
            try { latch.await(); return } catch (_: InterruptedException) { }
        }
    }
}
