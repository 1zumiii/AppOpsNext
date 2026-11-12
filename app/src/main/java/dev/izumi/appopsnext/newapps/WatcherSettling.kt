package dev.izumi.appopsnext.newapps

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the install watcher is between two states, so the switch that starts
 * and stops it waits until the last change has finished.
 *
 * Stopping is the part that matters. Shizuku's client keeps one connection
 * object per service and drops it only once the old watcher's death reaches the
 * app, after the disconnect callback. A bind made before that reuses the dying
 * object, which then forgets the new connection: the new watcher starts but
 * never connects, and only the connect timeout brings it back. So a bind after
 * a stop waits for that end, or for [endWaitMillis] at most, since a watcher
 * that never connected may never report one.
 *
 * Not thread safe: the client calls it under its own lock.
 */
internal class WatcherSettling(private val endWaitMillis: Long = END_WAIT_MILLIS) {
    private val mutableSettling = MutableStateFlow(false)
    val settling: StateFlow<Boolean> = mutableSettling.asStateFlow()

    /** When the stop that the end is awaited for happened, or null when none is awaited. */
    private var stoppedAtMillis: Long? = null
    private var starting = false

    /** The reason of a bind asked for while the end was awaited. */
    private var deferredReason: String? = null

    val awaitingEnd: Boolean
        get() = stoppedAtMillis != null

    /**
     * The watcher was turned off. [hadWatcher] says whether one was bound or
     * connected, whose end is then awaited. Returns the delay after which
     * [onEndOverdue] is due, or null when nothing is awaited.
     */
    fun onStopped(hadWatcher: Boolean, nowMillis: Long): Long? {
        starting = false
        deferredReason = null
        // A second stop finds nothing bound; the first one's wait still stands.
        if (hadWatcher) stoppedAtMillis = nowMillis
        publish()
        return if (hadWatcher) endWaitMillis else null
    }

    /** Whether a bind may go ahead now. When not, it is kept for [onEndSeen]. */
    fun mayBind(reason: String): Boolean {
        if (stoppedAtMillis == null) return true
        deferredReason = reason
        return false
    }

    /** Turned on: the switch waits until [onStartSettled]. */
    fun onStartRequested() {
        starting = true
        publish()
    }

    /** Connected, timed out, failed to bind or found Shizuku away. */
    fun onStartSettled() {
        starting = false
        publish()
    }

    /** The old watcher's end was seen. Returns the reason of a deferred bind, which is due now. */
    fun onEndSeen(): String? {
        if (stoppedAtMillis == null) return null
        stoppedAtMillis = null
        return takeDeferred()
    }

    /**
     * The end did not come within [endWaitMillis]. Returns null when it came
     * in the meantime, otherwise how long was waited and the deferred bind's reason.
     */
    fun onEndOverdue(nowMillis: Long): Overdue? {
        val stoppedAt = stoppedAtMillis ?: return null
        stoppedAtMillis = null
        return Overdue(nowMillis - stoppedAt, takeDeferred())
    }

    data class Overdue(val waitedMillis: Long, val deferredReason: String?)

    private fun takeDeferred(): String? {
        val reason = deferredReason
        deferredReason = null
        publish()
        return reason
    }

    private fun publish() {
        mutableSettling.value = starting || stoppedAtMillis != null
    }

    companion object {
        const val END_WAIT_MILLIS = 5_000L
    }
}
