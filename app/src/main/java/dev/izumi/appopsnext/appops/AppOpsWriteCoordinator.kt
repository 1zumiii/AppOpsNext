package dev.izumi.appopsnext.appops

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes whole write transactions, including package/UID retries and rollback. */
class AppOpsWriteCoordinator(
    /** A monotonic clock in milliseconds, so a changed wall clock does not shift it. */
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val mutex = Mutex()

    /** Read and written under [mutex] only. */
    private var lastWriteEndMillis: Long? = null

    internal suspend fun <T> serialize(action: suspend () -> T): T =
        mutex.withLock {
            try {
                action()
            } finally {
                lastWriteEndMillis = clock()
            }
        }

    /**
     * Runs [action] while no write runs, without counting as a write. It gets
     * the time since the last write ended, null when there was none.
     */
    internal suspend fun <T> betweenWrites(action: suspend (sinceLastWriteMillis: Long?) -> T): T =
        mutex.withLock { action(lastWriteEndMillis?.let { clock() - it }) }

    companion object {
        // All repositories use the same process-wide queue. A package-only key
        // would miss conflicts between packages sharing a UID.
        val Shared = AppOpsWriteCoordinator()
    }
}
