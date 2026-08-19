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
                    preferences[Keys.HIDE_SYSTEM_APPS]
                        ?: UserSettingsDefaults.HIDE_SYSTEM_APPS,
                suppressDenyFallbackNotice =
                    preferences[Keys.SUPPRESS_DENY_FALLBACK_NOTICE]
                        ?: UserSettingsDefaults
                            .SUPPRESS_DENY_FALLBACK_NOTICE,
                autoApplyNewAppTemplate =
                    preferences[Keys.AUTO_APPLY_NEW_APP_TEMPLATE]
                        ?: UserSettingsDefaults
                            .AUTO_APPLY_NEW_APP_TEMPLATE,
            )
        }

    suspend fun setHideSystemApps(hidden: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.HIDE_SYSTEM_APPS] = hidden
        }
    }

    suspend fun setDenyFallbackNoticeSuppressed(suppressed: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.SUPPRESS_DENY_FALLBACK_NOTICE] = suppressed
        }
    }

    suspend fun setAutoApplyNewAppTemplate(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.AUTO_APPLY_NEW_APP_TEMPLATE] = enabled
        }
    }

    private object Keys {
        val HIDE_SYSTEM_APPS = booleanPreferencesKey("hide_system_apps")
        val SUPPRESS_DENY_FALLBACK_NOTICE =
            booleanPreferencesKey("suppress_deny_fallback_notice")
        val AUTO_APPLY_NEW_APP_TEMPLATE =
            booleanPreferencesKey("auto_apply_new_app_template")
    }
}
