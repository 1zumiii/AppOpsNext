package dev.izumi.appopsnext.guard

import dev.izumi.appopsnext.appops.command.AppOpMode

/**
 * A limited number of restore attempts per rule in 24 hours, so a system that
 * keeps resetting is not fought. The user picks the number within [LIMIT_RANGE].
 */
internal object GuardRestorePolicy {
    const val DEFAULT_LIMIT = 3
    val LIMIT_RANGE = 1..20
    const val WINDOW_MILLIS = 24 * 60 * 60 * 1000L

    /** A delay of a day at most, the length of the attempt window. */
    val DELAY_RANGE = 0..1440

    fun recent(times: List<Long>, now: Long): List<Long> = times.filter { now - it in 0 until WINDOW_MILLIS }

    fun allows(times: List<Long>, now: Long, limit: Int): Boolean = recent(times, now).size < limit

    fun delayMillis(rule: GuardRule): Long = rule.restoreDelayMinutes.coerceIn(DELAY_RANGE) * 60_000L

    /**
     * When the earliest delayed restore falls due, among rules still waiting on their
     * delay. A rule already past it is left to the next regular check, so a failed
     * restore is not retried at once and does not spend the attempts in a loop.
     */
    fun nextDue(rules: List<GuardRule>, states: Map<String, GuardRuleState>, now: Long, limit: Int): Long? =
        rules.mapNotNull { rule ->
            val state = states[rule.id] ?: return@mapNotNull null
            val since = state.driftSince ?: return@mapNotNull null
            if (!rule.autoRestore || rule.restoreDelayMinutes <= 0) return@mapNotNull null
            if (state.lastObserved == null || state.lastObserved == rule.expectedMode) return@mapNotNull null
            if (!allows(state.restoreTimes, now, limit)) return@mapNotNull null
            (since + delayMillis(rule)).takeIf { it > now }
        }.minOrNull()
}

/**
 * Compares each guarded setting with the device and decides what to report.
 *
 * A drift is reported once per observed mode, not on every check that still
 * finds it. A rule with automatic restore writes the expected mode back while
 * [GuardRestorePolicy] allows it, then reports the limit once. A rule with a
 * restore delay waits until the change has been seen for that long.
 */
class GuardChecker(
    private val readMode: suspend (GuardRule) -> AppOpMode?,
    private val restore: suspend (GuardRule) -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Result(
        val states: Map<String, GuardRuleState>,
        val events: List<GuardEvent>,
        /** Rules whose mode could not be read; they keep their previous state. */
        val readFailures: Int,
    )

    suspend fun check(
        rules: List<GuardRule>,
        states: Map<String, GuardRuleState>,
        restoreLimit: Int = GuardRestorePolicy.DEFAULT_LIMIT,
    ): Result {
        val now = clock()
        val limit = restoreLimit.coerceIn(GuardRestorePolicy.LIMIT_RANGE)
        val updated = states.toMutableMap()
        val events = mutableListOf<GuardEvent>()
        var readFailures = 0
        for (rule in rules) {
            val previous = states[rule.id] ?: GuardRuleState()
            val observed = readMode(rule)
            if (observed == null) {
                readFailures++
                continue
            }
            var state = previous.copy(restoreTimes = GuardRestorePolicy.recent(previous.restoreTimes, now))
            if (observed == rule.expectedMode) {
                updated[rule.id] = state.copy(lastObserved = observed, driftSince = null)
                continue
            }
            // A change counts from the first check that saw it, across changes between other modes.
            val driftSince = previous.driftSince
                ?.takeIf { previous.lastObserved != null && previous.lastObserved != rule.expectedMode }
                ?: now
            state = state.copy(driftSince = driftSince)
            val isNew = previous.lastObserved != observed
            fun event(kind: GuardEventKind) = GuardEvent(
                timeMillis = now,
                packageName = rule.packageName,
                stableOperationName = rule.stableOperationName,
                scope = rule.scope,
                expectedMode = rule.expectedMode,
                observedMode = observed,
                kind = kind,
            )
            val waiting = now - driftSince < GuardRestorePolicy.delayMillis(rule)
            when {
                rule.autoRestore && !GuardRestorePolicy.allows(state.restoreTimes, now, limit) -> {
                    if (isNew) events += event(GuardEventKind.RESTORE_LIMIT)
                    state = state.copy(lastObserved = observed)
                }
                rule.autoRestore && waiting -> {
                    if (isNew) events += event(GuardEventKind.RESTORE_PENDING)
                    state = state.copy(lastObserved = observed)
                }
                rule.autoRestore -> {
                    val restored = restore(rule)
                    state = state.copy(
                        restoreTimes = state.restoreTimes + now,
                        lastObserved = if (restored) rule.expectedMode else observed,
                        driftSince = if (restored) null else driftSince,
                    )
                    events += event(if (restored) GuardEventKind.RESTORED else GuardEventKind.RESTORE_FAILED)
                }
                else -> {
                    if (isNew) events += event(GuardEventKind.DRIFT)
                    state = state.copy(lastObserved = observed)
                }
            }
            updated[rule.id] = state
        }
        // A realtime check covers a few rules; the others keep their state untouched.
        return Result(updated, events, readFailures)
    }
}
