package dev.izumi.appopsnext.history

import dev.izumi.appopsnext.history.model.AppOpHistoryEvent

/**
 * Takes the individual records inside an interval out of its access count, for
 * an interval the records cover only part of.
 *
 * Dropping such an interval loses the accesses in its uncovered part, and on the
 * reference device intervals from 11 to 30 days ago span 6 to 10 days. Keeping
 * it whole repeats the covered part. The remainder does neither: records are
 * matched on everything an interval is split by, so a record the interval does
 * not count is never taken out. The system may fold several accesses into one
 * record, so the remainder can be slightly high, never short.
 */
internal class IntervalRecordSubtraction(records: List<AppOpHistoryEvent>) {
    private val times: Map<IntervalKey, LongArray> = records
        .filterNot { it.isAggregated }
        .groupBy(::key) { it.accessTimeMillis }
        .mapValues { (_, times) -> times.toLongArray().apply { sort() } }

    /** Null when neither accesses beyond the records nor rejections are left. */
    fun remainder(interval: AppOpHistoryEvent): AppOpHistoryEvent? {
        val start = interval.intervalStartTimeMillis ?: interval.accessTimeMillis
        val inside = times[key(interval)]?.countBetween(start, interval.accessTimeMillis) ?: 0
        if (inside == 0) return interval
        val left = (interval.accessCount - inside).coerceAtLeast(0)
        // The duration covers the whole interval and cannot be split either.
        return when {
            left > 0 -> interval.copy(accessCount = left, durationMillis = null)
            else -> interval.rejectionsOnly()
        }
    }

    private data class IntervalKey(
        val uid: Int,
        val packageName: String,
        val attributionTag: String?,
        val uidState: String,
        val flags: String,
    )

    private fun key(event: AppOpHistoryEvent) =
        IntervalKey(event.uid, event.packageName, event.attributionTag, event.uidState, event.flags)

    /** Sorted times in [from, to], both ends included. */
    private fun LongArray.countBetween(from: Long, to: Long): Int =
        firstIndexAbove(to) - firstIndexAbove(from - 1)

    private fun LongArray.firstIndexAbove(value: Long): Int {
        var low = 0
        var high = size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (this[middle] <= value) low = middle + 1 else high = middle
        }
        return low
    }
}

/** An interval whose accesses are all held as individual records keeps only its rejections. */
internal fun AppOpHistoryEvent.rejectionsOnly(): AppOpHistoryEvent? =
    if (rejectCount > 0) copy(accessCount = 0, durationMillis = null) else null
