package dev.izumi.appopsnext.appops

import dev.izumi.appopsnext.appops.model.AppOpNames
import java.util.concurrent.ConcurrentHashMap

/** Process-local evidence. A rejected write says nothing about other apps or ops. */
class RevokedCompatWriteMemory {
    private data class Target(val packageName: String, val uid: Int, val operation: String)
    private val targets = ConcurrentHashMap.newKeySet<Target>()

    fun contains(packageName: String, uid: Int, operation: String): Boolean =
        Target(packageName, uid, AppOpNames.shellName(operation)) in targets

    fun record(packageName: String, uid: Int, operation: String) {
        targets.add(Target(packageName, uid, AppOpNames.shellName(operation)))
    }

    fun forget(packageName: String, uid: Int, operation: String) {
        targets.remove(Target(packageName, uid, AppOpNames.shellName(operation)))
    }
}
