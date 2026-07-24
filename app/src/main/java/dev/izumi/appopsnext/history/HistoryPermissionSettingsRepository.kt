package dev.izumi.appopsnext.history

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.izumi.appopsnext.appops.model.AppOpNames
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

    suspend fun add(operationName: String) {
        update { current ->
            val permission = HistoryPermission(
                AppOpNames.shellName(operationName),
            )
            if (permission in current) current else current + permission
        }
    }

    suspend fun remove(operationName: String) {
        val normalizedName = AppOpNames.shellName(operationName)
        update { current ->
            current.filterNot { it.shellOperationName == normalizedName }
        }
    }

    private suspend fun update(
        transform: (List<HistoryPermission>) -> List<HistoryPermission>,
    ) {
        dataStore.edit { preferences ->
            preferences[Keys.SELECTED_OPERATIONS] =
                HistoryPermissionSelectionCodec.encode(
                    transform(
                        HistoryPermissionSelectionCodec.decode(
                            preferences[Keys.SELECTED_OPERATIONS],
                        ),
                    ),
                )
        }
    }

    private object Keys {
        val SELECTED_OPERATIONS =
            stringPreferencesKey("selected_operations_v1")
    }
}
