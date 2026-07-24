package dev.izumi.appopsnext.history

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.izumi.appopsnext.history.model.HistoryPermission
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.historyPermissionSettingsDataStore:
    DataStore<Preferences> by preferencesDataStore(
        name = "history_permission_settings",
    )

class HistoryPermissionSettingsRepository(
    context: Context,
) {
    private val dataStore = context.historyPermissionSettingsDataStore

    val selectedPermissions: Flow<List<HistoryPermission>> =
        dataStore.data
            .catch { error ->
                if (error is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }
            .map { preferences ->
                HistoryPermissionSelectionCodec.decode(
                    preferences[Keys.SELECTED_OPERATIONS],
                )
            }

    suspend fun setSelected(permissions: List<HistoryPermission>) {
        dataStore.edit { preferences ->
            preferences[Keys.SELECTED_OPERATIONS] =
                HistoryPermissionSelectionCodec.encode(
                    permissions.distinct(),
                )
        }
    }

    private object Keys {
        val SELECTED_OPERATIONS =
            stringPreferencesKey("selected_operations_v1")
    }
}
