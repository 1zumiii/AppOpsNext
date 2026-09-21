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
                backgroundMonitor =
                    preferences[Keys.BACKGROUND_MONITOR]
                        ?: UserSettingsDefaults.BACKGROUND_MONITOR,
                monitorHeadsUp =
                    preferences[Keys.MONITOR_HEADS_UP]
                        ?: UserSettingsDefaults.MONITOR_HEADS_UP,
                suppressBatteryNotice =
                    preferences[Keys.SUPPRESS_BATTERY_NOTICE]
                        ?: UserSettingsDefaults.SUPPRESS_BATTERY_NOTICE,
                showAllMonitorOperations =
                    preferences[Keys.SHOW_ALL_MONITOR_OPERATIONS]
                        ?: UserSettingsDefaults.SHOW_ALL_MONITOR_OPERATIONS,
                suppressAllOperationsWarning =
                    preferences[Keys.SUPPRESS_ALL_OPERATIONS_WARNING]
                        ?: UserSettingsDefaults.SUPPRESS_ALL_OPERATIONS_WARNING,
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

    suspend fun setBackgroundMonitor(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.BACKGROUND_MONITOR] = enabled
        }
    }

    suspend fun setMonitorHeadsUp(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.MONITOR_HEADS_UP] = enabled
        }
    }

    suspend fun setShowAllMonitorOperations(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.SHOW_ALL_MONITOR_OPERATIONS] = enabled
        }
    }

    suspend fun setAllOperationsWarningSuppressed(suppressed: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.SUPPRESS_ALL_OPERATIONS_WARNING] = suppressed
        }
    }

    suspend fun setBatteryNoticeSuppressed(suppressed: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.SUPPRESS_BATTERY_NOTICE] = suppressed
        }
    }

    private object Keys {
        val HIDE_SYSTEM_APPS = booleanPreferencesKey("hide_system_apps")
        val SUPPRESS_DENY_FALLBACK_NOTICE =
            booleanPreferencesKey("suppress_deny_fallback_notice")
        val AUTO_APPLY_NEW_APP_TEMPLATE =
            booleanPreferencesKey("auto_apply_new_app_template")
        val BACKGROUND_MONITOR = booleanPreferencesKey("background_monitor")
        val MONITOR_HEADS_UP = booleanPreferencesKey("monitor_heads_up")
        val SUPPRESS_BATTERY_NOTICE =
            booleanPreferencesKey("suppress_battery_notice")
        val SHOW_ALL_MONITOR_OPERATIONS =
            booleanPreferencesKey("show_all_monitor_operations")
        // The key carries the wording it was answered for: "don't ask again" was
        // given for a particular warning, and a materially different one has to
        // be shown again rather than inheriting that answer.
        val SUPPRESS_ALL_OPERATIONS_WARNING =
            booleanPreferencesKey("suppress_all_operations_warning_noise_rom")
    }
}
