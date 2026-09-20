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

    suspend fun setOperations(packageName: String, operationNames: Set<String>) {
        dataStore.edit { preferences ->
            val current = MonitorTargetsCodec
                .decode(preferences[Keys.TARGETS])
                .filterNot { it.packageName == packageName }
            val updated = if (operationNames.isEmpty()) {
                current
            } else {
                current + MonitorTarget(packageName, operationNames)
            }
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
    }
}
