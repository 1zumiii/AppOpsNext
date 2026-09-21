package dev.izumi.appopsnext.monitor

import android.os.SystemClock
import dev.izumi.appopsnext.appops.PrivilegedAppOpsGateway
import dev.izumi.appopsnext.appops.parser.UidStateParser
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Answers whether an application was on screen when it used a permission.
 *
 * Only a point that asked to be told about background use needs this, and only
 * for an access that would otherwise be reported, so the question is asked as
 * rarely as the settings allow. Even then a busy operation can ask several times
 * a second, so an answer is reused for [cacheMillis]: a state that has just been
 * read is a better basis than running the dump again at that rate.
 *
 * An unanswerable question reports the access. A monitor that goes quiet because
 * it could not check something is worse than one that says a little too much.
 */
internal class ForegroundStateProbe(
    private val gateway: PrivilegedAppOpsGateway,
    private val cacheMillis: Long = CACHE_MILLIS,
    private val nowMillis: () -> Long = SystemClock::elapsedRealtime,
) {
    private class Entry(val readAt: Long, val states: Map<Int, String>)

    private val lock = Mutex()
    private val cache = mutableMapOf<String, Entry>()

    /** @return true only when the platform says this UID is the top application. */
    suspend fun isOnScreen(uid: Int, packageName: String): Boolean {
        val states = statesOf(packageName) ?: return false
        return states[uid] == UidStateParser.STATE_TOP
    }

    private suspend fun statesOf(packageName: String): Map<Int, String>? = lock.withLock {
        val now = nowMillis()
        val cached = cache[packageName]
        // A clock cannot run backwards here, but a cache that trusted it could
        // hold an answer for ever if one did.
        if (cached != null && now - cached.readAt in 0 until cacheMillis) {
            return@withLock cached.states
        }
        val states = runCatching {
            val result = gateway.getUidStates(packageName)
            if (result.exitCode != 0) null else UidStateParser.parse(result.stdout)
        }.getOrNull()
        if (states != null) {
            cache[packageName] = Entry(now, states)
            if (cache.size > MAX_CACHED_PACKAGES) evictOldest(now)
        }
        states
    }

    private fun evictOldest(now: Long) {
        cache.entries
            .sortedBy { it.value.readAt }
            .take(cache.size - MAX_CACHED_PACKAGES)
            .forEach { cache.remove(it.key) }
        // Anything already stale is worth dropping while we are here.
        cache.entries.removeAll { now - it.value.readAt >= cacheMillis }
    }

    private companion object {
        const val CACHE_MILLIS = 1_500L
        const val MAX_CACHED_PACKAGES = 32
    }
}
