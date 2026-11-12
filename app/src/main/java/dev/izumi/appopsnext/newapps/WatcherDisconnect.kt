package dev.izumi.appopsnext.newapps

/**
 * What a disconnect reported for the install watcher means. Shizuku reports
 * the watcher's binder dying also when the app ended the watcher itself or
 * when Shizuku went away, and only a loss of its own is worth a line and a
 * rebind.
 */
internal enum class WatcherDisconnect {
    /**
     * Turning the watcher off or the death of Shizuku already ended the
     * connection and wrote why. The disconnect is the echo of that.
     */
    ALREADY_ENDED,

    /**
     * Shizuku is gone and its death listener has not run yet. That listener
     * writes the loss, and the next Shizuku binder binds again; a rebind before
     * then cannot work.
     */
    SHIZUKU_GONE,

    /** The watcher went away while Shizuku stayed: written, and started again. */
    UNEXPECTED,
    ;

    companion object {
        fun of(bound: Boolean, connected: Boolean, shizukuAlive: Boolean): WatcherDisconnect = when {
            // A watcher that connected after its bind timed out is no longer
            // bound but still connected, so it counts as a loss of its own.
            !bound && !connected -> ALREADY_ENDED
            !shizukuAlive -> SHIZUKU_GONE
            else -> UNEXPECTED
        }
    }
}
