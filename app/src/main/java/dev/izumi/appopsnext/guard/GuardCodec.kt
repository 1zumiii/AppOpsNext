package dev.izumi.appopsnext.guard

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpScope

/**
 * Tab-separated records, one per string. Package and operation names are
 * validated tokens, so they never contain a tab. A record that does not parse
 * is dropped rather than failing the whole list.
 */
internal object GuardCodec {
    fun encodeRule(rule: GuardRule): String = listOf(
        RULE_V1, rule.packageName, rule.uid, rule.stableOperationName,
        rule.scope.name, rule.expectedMode.name, rule.autoRestore,
    ).joinToString(SEPARATOR)

    fun decodeRule(value: String): GuardRule? {
        val fields = value.split(SEPARATOR)
        if (fields.size != 7 || fields[0] != RULE_V1) return null
        return GuardRule(
            packageName = fields[1].takeIf(String::isNotBlank) ?: return null,
            uid = fields[2].toIntOrNull() ?: return null,
            stableOperationName = fields[3].takeIf(String::isNotBlank) ?: return null,
            scope = enumOrNull<AppOpScope>(fields[4]) ?: return null,
            expectedMode = enumOrNull<AppOpMode>(fields[5]) ?: return null,
            autoRestore = fields[6].toBooleanStrictOrNull() ?: return null,
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

    /** The rule id first, then the last mode seen (or `-`), then restore times. */
    fun encodeState(ruleId: String, state: GuardRuleState): String =
        (listOf(STATE_V1, ruleId, state.lastObserved?.name ?: NONE) + state.restoreTimes)
            .joinToString(SEPARATOR)

    fun decodeState(value: String): Pair<String, GuardRuleState>? {
        val fields = value.split(SEPARATOR)
        if (fields.size < 3 || fields[0] != STATE_V1) return null
        val lastObserved = if (fields[2] == NONE) null else enumOrNull<AppOpMode>(fields[2]) ?: return null
        val times = fields.drop(3).map { it.toLongOrNull() ?: return null }
        return fields[1] to GuardRuleState(lastObserved, times)
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
        enumValues<T>().firstOrNull { it.name == name }

    private const val SEPARATOR = "\t"
    private const val RULE_V1 = "R1"
    private const val EVENT_V1 = "E1"
    private const val STATE_V1 = "S1"
    private const val NONE = "-"
}
