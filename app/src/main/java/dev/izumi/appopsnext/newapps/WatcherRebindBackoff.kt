package dev.izumi.appopsnext.newapps

/**
 * Decides how long to wait before starting the install watcher again after it
 * went away. A watcher that keeps dying is not started forever: after the last
 * delay the next start waits for the app or Shizuku to start again. A watcher
 * that ran for [stableMillis] counts as healthy, so its next loss starts over.
 */
internal class WatcherRebindBackoff(
    private val delaysMillis: List<Long> = DEFAULT_DELAYS_MILLIS,
    private val stableMillis: Long = STABLE_MILLIS,
) {
    private var attempts = 0
    private var connectedAtMillis: Long? = null

    fun onConnected(nowMillis: Long) {
        connectedAtMillis = nowMillis
    }

    /** The delay before the next start, or null when no further start is due. */
    fun nextDelayMillis(nowMillis: Long): Long? {
        val connectedAt = connectedAtMillis
        connectedAtMillis = null
        if (connectedAt != null && nowMillis - connectedAt >= stableMillis) attempts = 0
        return delaysMillis.getOrNull(attempts)?.also { attempts++ }
    }

    fun reset() {
        attempts = 0
        connectedAtMillis = null
    }

    companion object {
        val DEFAULT_DELAYS_MILLIS = listOf(2_000L, 30_000L, 5 * 60_000L)
        const val STABLE_MILLIS = 10 * 60_000L
    }
}
