package dev.izumi.appopsnext.newapps

/**
 * Caps how many lines the install watcher writes to the diagnostic log within
 * a window, so a watcher that keeps restarting cannot push every other line
 * out of the log. The first line after a capped window says how many were
 * left out.
 */
internal class WatcherLogBudget(
    private val maxLines: Int = MAX_LINES_PER_WINDOW,
    private val windowMillis: Long = WINDOW_MILLIS,
) {
    private var windowStartMillis: Long? = null
    private var used = 0
    private var dropped = 0

    /**
     * Whether a line may be written now. When it may and lines were left out
     * before, [onDropped] gets their count first.
     */
    fun take(nowMillis: Long, onDropped: (Int) -> Unit): Boolean {
        val start = windowStartMillis
        if (start == null || nowMillis - start >= windowMillis || nowMillis < start) {
            windowStartMillis = nowMillis
            used = 0
        }
        if (used >= maxLines) {
            dropped++
            return false
        }
        if (dropped > 0) {
            // The note about the dropped lines takes one place of its own.
            onDropped(dropped)
            dropped = 0
            used++
        }
        used++
        return true
    }

    companion object {
        const val MAX_LINES_PER_WINDOW = 30
        const val WINDOW_MILLIS = 60 * 60_000L
    }
}
