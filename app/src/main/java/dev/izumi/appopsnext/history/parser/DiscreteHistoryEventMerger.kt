package dev.izumi.appopsnext.history.parser

import dev.izumi.appopsnext.history.model.AppOpHistoryEvent

/**
 * Collapses the duration-less companion that some Android builds emit beside
 * a duration-bearing discrete access.
 *
 * Events are paired only when all system-provided identity and access fields
 * match. Extra duration-less events remain visible, preserving distinct
 * accesses that happen to share a timestamp bucket.
 */
internal object DiscreteHistoryEventMerger {
    fun merge(
        events: List<AppOpHistoryEvent>,
    ): List<AppOpHistoryEvent> {
        val remainingDurationCompanions = events
            .asSequence()
            .filter { it.durationMillis != null }
            .groupingBy(::eventKey)
            .eachCount()
            .toMutableMap()

        return events.filter { event ->
            if (event.durationMillis != null) {
                true
            } else {
                val key = eventKey(event)
                val remaining = remainingDurationCompanions[key] ?: 0
                if (remaining > 0) {
                    remainingDurationCompanions[key] = remaining - 1
                    false
                } else {
                    true
                }
            }
        }
    }

    private fun eventKey(
        event: AppOpHistoryEvent,
    ): DiscreteHistoryEventKey =
        DiscreteHistoryEventKey(
            uid = event.uid,
            packageName = event.packageName,
            operationName = event.operationName,
            attributionTag = event.attributionTag,
            accessTimeMillis = event.accessTimeMillis,
            uidState = event.uidState,
            flags = event.flags,
        )

    private data class DiscreteHistoryEventKey(
        val uid: Int,
        val packageName: String,
        val operationName: String,
        val attributionTag: String?,
        val accessTimeMillis: Long,
        val uidState: String,
        val flags: String,
    )
}
