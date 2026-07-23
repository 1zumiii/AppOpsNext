package dev.izumi.appopsnext.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.userSettingsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "user_settings")

class UserSettingsRepository(
    context: Context,
) {
    private val dataStore = context.userSettingsDataStore

    val settings: Flow<UserSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences ->
            UserSettings(
                hideSystemApps =
                    preferences[Keys.HIDE_SYSTEM_APPS] ?: false,
            )
        }

    suspend fun setHideSystemApps(hidden: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.HIDE_SYSTEM_APPS] = hidden
        }
    }

    private object Keys {
        val HIDE_SYSTEM_APPS = booleanPreferencesKey("hide_system_apps")
    }
}
