package dev.izumi.appopsnext.guard

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Rules and the mode are settings worth restoring on another device. */
private val Context.guardRulesDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "guard_rules")

/** What happened on this device; kept out of backups like the history archive. */
private val Context.guardStateDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "guard_state")

class GuardRepository(context: Context) {
    private val rulesStore = context.guardRulesDataStore
    private val stateStore = context.guardStateDataStore

    val mode: Flow<GuardMode> = rulesStore.data.map { preferences ->
        GuardMode.entries.firstOrNull { it.name == preferences[MODE] } ?: GuardMode.OFF
    }

    /** Restore attempts allowed per rule in 24 hours. */
    val restoreLimit: Flow<Int> = rulesStore.data.map { preferences ->
        preferences[RESTORE_LIMIT]?.takeIf { it in GuardRestorePolicy.LIMIT_RANGE } ?: GuardRestorePolicy.DEFAULT_LIMIT
    }

    val rules: Flow<List<GuardRule>> = rulesStore.data.map { preferences ->
        preferences[RULES].orEmpty().mapNotNull(GuardCodec::decodeRule).sortedBy { it.id }
    }

    /** Newest first. */
    val events: Flow<List<GuardEvent>> = stateStore.data.map { preferences ->
        preferences[EVENTS].orEmpty().mapNotNull(GuardCodec::decodeEvent).sortedByDescending { it.timeMillis }
    }

    suspend fun setMode(mode: GuardMode) {
        rulesStore.edit { it[MODE] = mode.name }
    }

    suspend fun setRestoreLimit(limit: Int) {
        rulesStore.edit { it[RESTORE_LIMIT] = limit.coerceIn(GuardRestorePolicy.LIMIT_RANGE) }
    }

    /** Replaces a rule with the same package, operation and scope. */
    suspend fun upsert(rule: GuardRule) {
        rulesStore.edit { preferences ->
            val others = preferences[RULES].orEmpty().filter { GuardCodec.decodeRule(it)?.id != rule.id }
            preferences[RULES] = (others + GuardCodec.encodeRule(rule)).toSet()
        }
    }

    /** Replaces every rule of one setting, in either scope, in a single write. */
    suspend fun replaceSetting(
        packageName: String,
        stableOperationName: String,
        replacement: (existing: List<GuardRule>) -> GuardRule?,
    ) {
        rulesStore.edit { preferences ->
            val (existing, others) = preferences[RULES].orEmpty().partition { encoded ->
                GuardCodec.decodeRule(encoded)?.let {
                    it.packageName == packageName && it.stableOperationName == stableOperationName
                } == true
            }
            val rule = replacement(existing.mapNotNull(GuardCodec::decodeRule))
            preferences[RULES] = (others + listOfNotNull(rule?.let(GuardCodec::encodeRule))).toSet()
        }
    }

    /**
     * Records the settings a batch wrote, each as a rule for its new mode. A setting
     * that was guarded keeps its rule at the new mode and its automatic restore with
     * its delay. One
     * that was not is guarded only with [guardNew].
     */
    suspend fun applyBatch(changed: List<GuardRule>, guardNew: Boolean) {
        rulesStore.edit { preferences ->
            val current = preferences[RULES].orEmpty().mapNotNull(GuardCodec::decodeRule)
            val bySetting = current.groupBy { it.packageName to it.stableOperationName }
            val updates = changed.mapNotNull { rule ->
                val existing = bySetting[rule.packageName to rule.stableOperationName]
                when {
                    existing != null -> rule.keepingRestoreOf(existing)
                    guardNew -> rule
                    else -> null
                }
            }.associateBy { it.packageName to it.stableOperationName }
            if (updates.isEmpty()) return@edit
            val kept = current.filter { (it.packageName to it.stableOperationName) !in updates }
            preferences[RULES] = (kept + updates.values).map(GuardCodec::encodeRule).toSet()
        }
    }

    /**
     * Adds imported rules. An imported rule takes the place of the local rules of
     * the same setting, and the others stay. A null [mode] or [restoreLimit] leaves
     * that setting alone.
     */
    suspend fun import(mode: GuardMode?, imported: List<GuardRule>, restoreLimit: Int? = null) {
        val settings = imported.mapTo(mutableSetOf()) { it.packageName to it.stableOperationName }
        rulesStore.edit { preferences ->
            val kept = preferences[RULES].orEmpty().filter { encoded ->
                GuardCodec.decodeRule(encoded)?.let { (it.packageName to it.stableOperationName) in settings } != true
            }
            preferences[RULES] = (kept + imported.map(GuardCodec::encodeRule)).toSet()
            mode?.let { preferences[MODE] = it.name }
            restoreLimit?.let { preferences[RESTORE_LIMIT] = it.coerceIn(GuardRestorePolicy.LIMIT_RANGE) }
        }
    }

    suspend fun remove(ruleId: String) {
        rulesStore.edit { preferences ->
            preferences[RULES] = preferences[RULES].orEmpty().filter { GuardCodec.decodeRule(it)?.id != ruleId }.toSet()
        }
    }

    suspend fun states(): Map<String, GuardRuleState> =
        stateStore.data.first()[STATES].orEmpty().mapNotNull(GuardCodec::decodeState).toMap()

    /** Stores a check's outcome; states of rules that no longer exist are dropped. */
    suspend fun saveCheck(states: Map<String, GuardRuleState>, newEvents: List<GuardEvent>) {
        val ids = rules.first().mapTo(mutableSetOf()) { it.id }
        stateStore.edit { preferences ->
            preferences[STATES] = states.filterKeys { it in ids }
                .map { (id, state) -> GuardCodec.encodeState(id, state) }
                .toSet()
            if (newEvents.isNotEmpty()) {
                val kept = preferences[EVENTS].orEmpty().mapNotNull(GuardCodec::decodeEvent)
                preferences[EVENTS] = (kept + newEvents)
                    .sortedByDescending { it.timeMillis }
                    .take(MAX_EVENTS)
                    .map(GuardCodec::encodeEvent)
                    .toSet()
            }
        }
    }

    suspend fun clearEvents() {
        stateStore.edit { it.remove(EVENTS) }
    }

    private companion object {
        val MODE = stringPreferencesKey("mode")
        val RESTORE_LIMIT = intPreferencesKey("restore_limit")
        val RULES = stringSetPreferencesKey("rules_v1")
        val STATES = stringSetPreferencesKey("states_v1")
        val EVENTS = stringSetPreferencesKey("events_v1")
        const val MAX_EVENTS = 200
    }
}

/**
 * This rule with the automatic restore of the rules it replaces: on when any of
 * them had it, with the longest delay among those.
 */
internal fun GuardRule.keepingRestoreOf(existing: List<GuardRule>): GuardRule {
    val restoring = existing.filter { it.autoRestore }
    return copy(
        autoRestore = restoring.isNotEmpty(),
        restoreDelayMinutes = restoring.maxOfOrNull { it.restoreDelayMinutes } ?: 0,
    )
}
