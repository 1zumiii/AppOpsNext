package dev.izumi.appopsnext.guard

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.command.OperationNameValidator
import dev.izumi.appopsnext.appops.command.PackageNameValidator
import dev.izumi.appopsnext.appops.model.AppOpNames
import dev.izumi.appopsnext.appops.model.AppOpScope

/**
 * Tab-separated records, one per string. Package and operation names are
 * validated tokens, so they never contain a tab. A record that does not parse,
 * or names an invalid package or operation, is dropped rather than failing the
 * whole list.
 */
internal object GuardCodec {
    /**
     * A rule that restores at once is written as `R1`, so older versions still read
     * it from a backup. `R2` adds the delay and whether to wait for the app to leave.
     */
    fun encodeRule(rule: GuardRule): String {
        val plain = rule.restoreDelayMinutes == 0 && !rule.restoreAfterLeaving
        val fields = listOf(
            if (plain) RULE_V1 else RULE_V2, rule.packageName, rule.uid,
            rule.stableOperationName, rule.scope.name, rule.expectedMode.name, rule.autoRestore,
        )
        return (if (plain) fields else fields + rule.restoreDelayMinutes + rule.restoreAfterLeaving)
            .joinToString(SEPARATOR)
    }

    fun decodeRule(value: String): GuardRule? {
        val fields = value.split(SEPARATOR)
        val delay = when {
            fields.size == 7 && fields[0] == RULE_V1 -> 0
            // Builds between 1.6.8-beta1 and the next release wrote `R2` without the last field.
            fields.size in 8..9 && fields[0] == RULE_V2 ->
                fields[7].toIntOrNull()?.takeIf { it in GuardRestorePolicy.DELAY_RANGE } ?: return null
            else -> return null
        }
        val afterLeaving = if (fields.size == 9) fields[8].toBooleanStrictOrNull() ?: return null else false
        return GuardRule(
            packageName = fields[1].takeIf(PackageNameValidator::isValid) ?: return null,
            uid = fields[2].toIntOrNull() ?: return null,
            stableOperationName = fields[3]
                .takeIf { OperationNameValidator.isValid(AppOpNames.shellName(it)) }
                ?: return null,
            scope = enumOrNull<AppOpScope>(fields[4]) ?: return null,
            expectedMode = enumOrNull<AppOpMode>(fields[5]) ?: return null,
            autoRestore = fields[6].toBooleanStrictOrNull() ?: return null,
            restoreDelayMinutes = delay,
            restoreAfterLeaving = afterLeaving,
        )
    }

    fun encodeEvent(event: GuardEvent): String = listOf(
        EVENT_V1, event.timeMillis, event.packageName, event.stableOperationName,
        event.scope.name, event.expectedMode.name, event.observedMode.name, event.kind.name,
    ).joinToString(SEPARATOR)

    fun decodeEvent(value: String): GuardEvent? {
        val fields = value.split(SEPARATOR)
        if (fields.size != 8 || fields[0] != EVENT_V1) return null
        return GuardEvent(
            timeMillis = fields[1].toLongOrNull() ?: return null,
            packageName = fields[2],
            stableOperationName = fields[3],
            scope = enumOrNull<AppOpScope>(fields[4]) ?: return null,
            expectedMode = enumOrNull<AppOpMode>(fields[5]) ?: return null,
            observedMode = enumOrNull<AppOpMode>(fields[6]) ?: return null,
            kind = enumOrNull<GuardEventKind>(fields[7]) ?: return null,
        )
    }

    /**
     * The rule id first, then the last mode seen (or `-`), then restore times. `S2`
     * puts when the current change was first seen (or `-`) and whether the app held
     * it on screen (`H` or `-`) before the times.
     */
    fun encodeState(ruleId: String, state: GuardRuleState): String =
        (
            listOf(
                STATE_V2, ruleId, state.lastObserved?.name ?: NONE,
                state.driftSince?.toString() ?: NONE, if (state.heldOnScreen) HELD else NONE,
            ) + state.restoreTimes
        ).joinToString(SEPARATOR)

    fun decodeState(value: String): Pair<String, GuardRuleState>? {
        val fields = value.split(SEPARATOR)
        val timesFrom = when (fields.firstOrNull()) {
            STATE_V1 -> 3
            STATE_V2 -> 5
            else -> return null
        }
        if (fields.size < timesFrom) return null
        val lastObserved = if (fields[2] == NONE) null else enumOrNull<AppOpMode>(fields[2]) ?: return null
        val driftSince = when {
            timesFrom == 3 || fields[3] == NONE -> null
            else -> fields[3].toLongOrNull() ?: return null
        }
        val heldOnScreen = when {
            timesFrom == 3 || fields[4] == NONE -> false
            fields[4] == HELD -> true
            else -> return null
        }
        val times = fields.drop(timesFrom).map { it.toLongOrNull() ?: return null }
        return fields[1] to GuardRuleState(lastObserved, times, driftSince, heldOnScreen)
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
        enumValues<T>().firstOrNull { it.name == name }

    private const val SEPARATOR = "\t"
    private const val RULE_V1 = "R1"
    private const val RULE_V2 = "R2"
    private const val EVENT_V1 = "E1"
    private const val STATE_V1 = "S1"
    private const val STATE_V2 = "S2"
    private const val NONE = "-"
    private const val HELD = "H"
}
