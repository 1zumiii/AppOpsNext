package dev.izumi.appopsnext.presentation.history

import dev.izumi.appopsnext.history.ArchivedHistory
import dev.izumi.appopsnext.history.ArchiveKey
import dev.izumi.appopsnext.history.IntervalRecordSubtraction
import dev.izumi.appopsnext.history.archiveKey
import dev.izumi.appopsnext.history.rejectionsOnly
import dev.izumi.appopsnext.history.takeCopy

/**
 * Puts saved individual records beside the system's current history. Where the
 * saved records cover a period, an interval's access count would repeat them,
 * so it keeps only its rejections; an interval in a gap still fills that gap.
 * One that only partly overlaps keeps the accesses beyond the saved records
 * inside it, so its uncovered part is neither lost nor counted twice.
 */
object HistoryArchiveMerger {
    fun merge(
        system: List<ResolvedHistoryEvent>,
        archived: ArchivedHistory?,
    ): List<ResolvedHistoryEvent> {
        if (archived == null) return system
        // Identical records are separate accesses in one minute, so both sides are
        // matched copy for copy: the saved side only adds what the system no longer has.
        val systemIndividual = system.filterNot { it.event.isAggregated }
        val systemCopies = HashMap<ArchiveKey, Int>()
        systemIndividual.forEach { systemCopies.merge(it.archiveKey(), 1, Int::plus) }
        // The access time is part of the key, so a saved record outside the system's
        // span cannot match, and most of a large archive is kept without hashing it.
        val systemFrom = systemIndividual.minOfOrNull { it.event.accessTimeMillis } ?: Long.MAX_VALUE
        val systemTo = systemIndividual.maxOfOrNull { it.event.accessTimeMillis } ?: Long.MIN_VALUE
        val archivedOnly = archived.events.filter { saved ->
            !saved.event.isAggregated &&
                (saved.event.accessTimeMillis !in systemFrom..systemTo || !systemCopies.takeCopy(saved.archiveKey()))
        }
        // The system's own records were already taken out of its intervals when
        // they were read, so only the saved records it no longer has are taken here.
        val savedRecords = IntervalRecordSubtraction(archivedOnly.map { it.event })
        val intervals = system.filter { it.event.isAggregated }.mapNotNull { interval ->
            val start = interval.event.intervalStartTimeMillis ?: interval.event.accessTimeMillis
            val end = interval.event.accessTimeMillis
            val reduced = when {
                interval.event.accessCount == 0 -> interval.event
                archived.coverage.any { start >= it.first && end <= it.last } -> interval.event.rejectionsOnly()
                archived.coverage.any { start <= it.last && it.first <= end } -> savedRecords.remainder(interval.event)
                else -> interval.event
            }
            reduced?.let { interval.copy(event = it) }
        }
        val individual = systemIndividual + archivedOnly
        return (individual + intervals).sortedByDescending { it.event.accessTimeMillis }
    }

}
