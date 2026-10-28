package dev.izumi.appopsnext.guard

import dev.izumi.appopsnext.appops.command.AppOpMode

/** At most three restore attempts per rule in 24 hours, so a system that keeps resetting is not fought. */
internal object GuardRestorePolicy {
    const val MAX_ATTEMPTS = 3
    const val WINDOW_MILLIS = 24 * 60 * 60 * 1000L

    fun recent(times: List<Long>, now: Long): List<Long> = times.filter { now - it in 0 until WINDOW_MILLIS }

    fun allows(times: List<Long>, now: Long): Boolean = recent(times, now).size < MAX_ATTEMPTS
}

/**
 * Compares each guarded setting with the device and decides what to report.
 *
 * A drift is reported once per observed mode, not on every check that still
 * finds it. A rule with automatic restore writes the expected mode back while
 * [GuardRestorePolicy] allows it, then reports the limit once.
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

    suspend fun check(rules: List<GuardRule>, states: Map<String, GuardRuleState>): Result {
        val now = clock()
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
                updated[rule.id] = state.copy(lastObserved = observed)
                continue
            }
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
            when {
                rule.autoRestore && GuardRestorePolicy.allows(state.restoreTimes, now) -> {
                    val restored = restore(rule)
                    state = state.copy(
                        restoreTimes = state.restoreTimes + now,
                        lastObserved = if (restored) rule.expectedMode else observed,
                    )
                    events += event(if (restored) GuardEventKind.RESTORED else GuardEventKind.RESTORE_FAILED)
                }
                rule.autoRestore -> {
                    if (isNew) events += event(GuardEventKind.RESTORE_LIMIT)
                    state = state.copy(lastObserved = observed)
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
