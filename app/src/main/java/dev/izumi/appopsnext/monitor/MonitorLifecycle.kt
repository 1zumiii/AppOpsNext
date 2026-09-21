package dev.izumi.appopsnext.monitor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes the privileged resource; the gate also orders invalidation and publication. */
internal class MonitorLifecycle<T>(private val scope: CoroutineScope, private val close: () -> Unit) {
    class Session(parent: CoroutineScope) {
        val job = SupervisorJob(parent.coroutineContext[Job])
        val scope = CoroutineScope(parent.coroutineContext + job)
        internal var revision = 0L
    }

    private val mutex = Mutex()
    private val gate = Any()
    private var generation = 0L
    private var current: Session? = null
    private var value: T? = null

    fun session(): Session? = synchronized(gate) { current }

    fun revision(session: Session): Long? = publish(session) { session.revision }

    fun <R> publish(session: Session, revision: Long? = null, block: () -> R): R? =
        synchronized(gate) {
            if (current !== session || !session.job.isActive ||
                (revision != null && revision != session.revision)
            ) null else block()
        }

    fun clear(block: () -> Unit) = synchronized(gate) {
        current?.let { it.revision++; block() }
    }

    fun whenStopped(block: () -> Unit) = synchronized(gate) {
        if (current == null) block()
    }

    suspend fun start(register: suspend (Session) -> T): T {
        val requestedGeneration = synchronized(gate) { generation }
        return mutex.withLock {
            currentCoroutineContext().ensureActive()
            synchronized(gate) {
                if (generation != requestedGeneration) throw CancellationException("Monitor stopped")
                value?.let { return@withLock it }
            }
            close()
            val session = synchronized(gate) {
                if (generation != requestedGeneration) throw CancellationException("Monitor stopped")
                Session(scope).also { current = it }
            }
            try {
                val result = register(session)
                currentCoroutineContext().ensureActive()
                publish(session) { value = result; true }
                    ?: throw CancellationException("Monitor stopped during registration")
                result
            } catch (error: Throwable) {
                close()
                synchronized(gate) {
                    if (current === session) { current = null; value = null }
                    session.job.cancel()
                }
                throw error
            }
        }
    }

    suspend fun reconnect(session: Session, register: () -> T): T = mutex.withLock {
        currentCoroutineContext().ensureActive()
        check(publish(session) { true } == true) { "Monitor session ended" }
        close()
        val result = register()
        publish(session) { value = result; true }
            ?: run { close(); throw CancellationException("Monitor stopped during recovery") }
        result
    }

    /** Invalidates synchronously even if a blocking registration is still in flight. */
    fun stop(onStopped: () -> Unit): Job {
        synchronized(gate) {
            generation++
            current?.job?.cancel()
            current = null
            value = null
            onStopped()
        }
        return scope.launch {
            mutex.withLock {
                // A newer session already closed the old resource before registering.
                if (session() == null) close()
            }
        }
    }
}

/** A single recovery cycle; cancellation never becomes a retryable registration error. */
internal suspend fun <T> retryMonitorRegistration(
    pause: suspend (Long) -> Unit = { delay(it) },
    register: suspend () -> T,
): Result<T> {
    var lastError: Exception? = null
    for (backoff in longArrayOf(0, 1_000, 3_000, 10_000)) {
        pause(backoff)
        try {
            return Result.success(register())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            lastError = error
        }
    }
    return Result.failure(checkNotNull(lastError))
}
