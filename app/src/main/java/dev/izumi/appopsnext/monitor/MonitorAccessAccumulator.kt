package dev.izumi.appopsnext.monitor

/**
 * Collects reported accesses for display.
 *
 * Every access the monitor is told about is reported, except that a point may
 * carry a shortest gap between two of its reports. That is the only thing that
 * ever suppresses one: how often a monitoring point is worth hearing about
 * depends on what the application does with it, which nothing about the callback
 * itself can say.
 *
 * The one suppression that is not configured is [DUPLICATE_CALLBACK_MILLIS]: the
 * platform reports a single clipboard read twice, four to ten milliseconds
 * apart, so without it one read would be reported as two.
 */
internal class MonitorAccessAccumulator {
    private data class Subject(
        val uid: Int,
        val packageName: String,
        val operationName: String,
    )

    /** When each point was last reported, which a throttle is measured from. */
    private val lastReported = mutableMapOf<Subject, Long>()

    var accesses: List<MonitoredAccess> = emptyList()
        private set

    fun clear() {
        accesses = emptyList()
        lastReported.clear()
    }

    /**
     * @param throttleMillis the shortest gap between two reports of this point,
     * or null to report every access.
     * @return whether the access was reported, which is what may raise an alert.
     */
    /**
     * Whether [add] would report this access, without recording it.
     *
     * Asking the platform where an application was running costs a command, so
     * it is only worth asking for an access that has already got past the
     * cheaper filters.
     */
    fun wouldReport(access: MonitoredAccess, throttleMillis: Long?): Boolean {
        val gap = throttleMillis ?: DUPLICATE_CALLBACK_MILLIS
        val last = lastReported[access.subject()] ?: return true
        // A clock that has gone backwards cannot be used to hold an access back.
        return access.elapsedRealtimeMillis - last !in 0 until gap
    }

    fun add(access: MonitoredAccess, throttleMillis: Long?): Boolean {
        if (!wouldReport(access, throttleMillis)) return false
        val subject = access.subject()
        lastReported[subject] = access.elapsedRealtimeMillis
        val index = accesses.indexOfFirst {
            it.subject() == subject && it.allowed == access.allowed
        }
        val previous = accesses.getOrNull(index)
        // The incoming access carries the current label and times; only the count
        // is inherited.
        val updated = access.copy(count = (previous?.count ?: 0) + 1)
        accesses = (listOf(updated) + accesses.filterIndexed { at, _ -> at != index })
            .take(MAX_ENTRIES)
        lastReported.keys.retainAll(accesses.mapTo(mutableSetOf()) { it.subject() })
        return true
    }

    private fun MonitoredAccess.subject() = Subject(uid, packageName, operationName)

    companion object {
        /** One read reported twice, measured four to ten milliseconds apart. */
        const val DUPLICATE_CALLBACK_MILLIS = 200L
        private const val MAX_ENTRIES = 100
    }
}
