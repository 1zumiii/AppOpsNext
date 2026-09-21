package dev.izumi.appopsnext.monitor

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.monitorTargetsDataStore:
    DataStore<Preferences> by preferencesDataStore(name = "monitor_targets")

class MonitorTargetsRepository(
    context: Context,
) {
    private val dataStore = context.monitorTargetsDataStore

    val targets: Flow<List<MonitorTarget>> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> MonitorTargetsCodec.decode(preferences[Keys.TARGETS]) }

    /**
     * Per-point settings, including ones whose point is no longer watched.
     *
     * Unpicking an operation is not a decision about how it should be reported,
     * so the settings are kept: a point that comes back comes back configured.
     */
    val pointSettings: Flow<List<MonitorPointSettings>> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> MonitorPointSettingsCodec.decode(preferences[Keys.POINTS]) }

    suspend fun setOperations(packageName: String, operationNames: Set<String>) {
        update(packageName) {
            if (operationNames.isEmpty()) {
                null
            } else {
                MonitorTarget(packageName, operationNames)
            }
        }
    }

    /** @param seconds null switches the throttle off, reporting every access. */
    suspend fun setThrottle(packageName: String, operationName: String, seconds: Int?) {
        if (seconds != null && !MonitorThrottles.isValid(seconds)) return
        updatePoint(packageName, operationName) { it.copy(throttleSeconds = seconds) }
    }

    /** @param headsUp null follows the monitor's own notification setting. */
    suspend fun setPointHeadsUp(packageName: String, operationName: String, headsUp: Boolean?) {
        updatePoint(packageName, operationName) { it.copy(headsUp = headsUp) }
    }

    suspend fun setPointOutcomes(
        packageName: String,
        operationName: String,
        outcomes: MonitorOutcomes,
    ) {
        updatePoint(packageName, operationName) { it.copy(outcomes = outcomes) }
    }

    suspend fun setPointBackgroundOnly(
        packageName: String,
        operationName: String,
        backgroundOnly: Boolean,
    ) {
        updatePoint(packageName, operationName) { it.copy(backgroundOnly = backgroundOnly) }
    }

    private suspend fun updatePoint(
        packageName: String,
        operationName: String,
        change: (MonitorPointSettings) -> MonitorPointSettings,
    ) {
        dataStore.edit { preferences ->
            val current = MonitorPointSettingsCodec.decode(preferences[Keys.POINTS])
            val existing = current.firstOrNull {
                it.packageName == packageName && it.operationName == operationName
            } ?: MonitorPointSettings(packageName, operationName)
            val updated = current.filterNot {
                it.packageName == packageName && it.operationName == operationName
            } + change(existing)
            preferences[Keys.POINTS] = MonitorPointSettingsCodec.encode(
                updated.sortedWith(
                    compareBy(
                        MonitorPointSettings::packageName,
                        MonitorPointSettings::operationName,
                    ),
                ),
            )
        }
    }

    private suspend fun update(
        packageName: String,
        change: (MonitorTarget?) -> MonitorTarget?,
    ) {
        dataStore.edit { preferences ->
            val current = MonitorTargetsCodec.decode(preferences[Keys.TARGETS])
            val existing = current.firstOrNull { it.packageName == packageName }
            val replacement = change(existing)
            val updated = current.filterNot { it.packageName == packageName } +
                listOfNotNull(replacement)
            preferences[Keys.TARGETS] = MonitorTargetsCodec.encode(
                updated.sortedBy(MonitorTarget::packageName),
            )
        }
    }

    suspend fun clear() {
        dataStore.edit { preferences -> preferences[Keys.TARGETS] = "" }
    }

    private object Keys {
        val TARGETS = stringPreferencesKey("monitor_targets")
        val POINTS = stringPreferencesKey("monitor_throttles")
    }
}
