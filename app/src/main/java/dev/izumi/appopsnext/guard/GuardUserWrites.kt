package dev.izumi.appopsnext.guard

import dev.izumi.appopsnext.appops.model.AppOpNames
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class GuardWriteTarget(val packageName: String, val uid: Int, val operationName: String) {
    val stableOperationName: String = AppOpNames.stableName(operationName)
}

/** Keeps a guard check from acting on a mode while its user write is in progress. */
internal class GuardUserWrites(private val checkMutex: Mutex) {
    // Keep concurrent user writes and their rule updates in the same order.
    private val userWriteMutex = Mutex()
    private var active = emptyMap<String, List<GuardWriteTarget>>()
    private val skipped = mutableSetOf<String>()

    suspend fun <T> run(
        targets: Collection<GuardWriteTarget>,
        recheck: (Set<String>) -> Unit,
        action: suspend () -> T,
    ): T {
        if (targets.isEmpty()) return action()
        return userWriteMutex.withLock {
            // Wait out a check already using a rule snapshot before changing the device.
            checkMutex.withLock { active = targets.groupBy { it.stableOperationName } }
            try {
                action()
            } finally {
                val operations = withContext(NonCancellable) {
                    checkMutex.withLock {
                        active = emptyMap()
                        skipped.toSet().also { skipped.clear() }
                    }
                }
                if (operations.isNotEmpty()) recheck(operations)
            }
        }
    }

    /** Called under [checkMutex]. Either side can fall back to writing the shared UID. */
    fun shouldSkip(rule: GuardRule): Boolean {
        val writing = active[rule.stableOperationName].orEmpty().any {
            it.packageName == rule.packageName || it.uid == rule.uid
        }
        if (writing) skipped += rule.stableOperationName
        return writing
    }
}
