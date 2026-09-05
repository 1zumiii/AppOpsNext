package dev.izumi.appopsnext.appops

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes whole write transactions, including package/UID retries and rollback. */
class AppOpsWriteCoordinator {
    private val mutex = Mutex()

    internal suspend fun <T> serialize(action: suspend () -> T): T =
        mutex.withLock { action() }

    companion object {
        // All repositories use the same process-wide queue. A package-only key
        // would miss conflicts between packages sharing a UID.
        val Shared = AppOpsWriteCoordinator()
    }
}
