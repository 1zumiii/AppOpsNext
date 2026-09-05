package dev.izumi.appopsnext.nativebackend

import java.io.Closeable
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One ordered pipe, with a deadline independent of the remote process. */
internal class NativeDaemonChannel(
    private val exchange: (String) -> String,
    private val abort: () -> Unit,
    private val timeoutMillis: Long = 12_000,
) : Closeable {
    private val requests = Mutex()
    private val closed = AtomicBoolean(false)
    private val active = AtomicReference<Future<String>?>(null)
    private val worker = Executors.newSingleThreadExecutor { action ->
        Thread(action, "AppOps-daemon-pipe").apply { isDaemon = true }
    }

    suspend fun request(line: String): String = requests.withLock {
        withContext(Dispatchers.IO) {
            check(!closed.get()) { "Native daemon is unavailable" }
            val task = worker.submit<String> { exchange(line) }
            active.set(task)
            if (closed.get()) task.cancel(true)
            try {
                task.get(timeoutMillis, TimeUnit.MILLISECONDS)
            } catch (error: Exception) {
                close()
                throw IOException("Native daemon request failed or timed out", error)
            } finally {
                active.compareAndSet(task, null)
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        active.getAndSet(null)?.cancel(true)
        worker.shutdownNow()
        // Never wait for the pipe lock, write EXIT, or make a blocking Binder
        // call on the caller (which may be the UI thread).
        Thread({ runCatching(abort) }, "AppOps-daemon-close").apply {
            isDaemon = true
            start()
        }
    }
}
