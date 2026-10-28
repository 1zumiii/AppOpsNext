package dev.izumi.appopsnext.guard

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
        val RULES = stringSetPreferencesKey("rules_v1")
        val STATES = stringSetPreferencesKey("states_v1")
        val EVENTS = stringSetPreferencesKey("events_v1")
        const val MAX_EVENTS = 200
    }
}
