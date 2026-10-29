package dev.izumi.appopsnext.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.izumi.appopsnext.apps.AppSortOrder
import dev.izumi.appopsnext.backup.UserSettingsBackup
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
                suppressPermissionFlagNotice =
                    preferences[Keys.SUPPRESS_PERMISSION_FLAG_NOTICE]
                        ?: UserSettingsDefaults
                            .SUPPRESS_PERMISSION_FLAG_NOTICE,
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
                saveIndividualHistory =
                    preferences[Keys.SAVE_INDIVIDUAL_HISTORY]
                        ?: UserSettingsDefaults.SAVE_INDIVIDUAL_HISTORY,
                savedHistoryOperations = preferences[Keys.SAVED_HISTORY_OPERATIONS],
                appSortOrder = AppSortOrder.fromName(preferences[Keys.APP_SORT_ORDER])
                    ?: AppSortOrder.NAME,
            )
        }

    suspend fun setHideSystemApps(hidden: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.HIDE_SYSTEM_APPS] = hidden
        }
    }

    suspend fun setAppSortOrder(order: AppSortOrder) {
        dataStore.edit { preferences ->
            preferences[Keys.APP_SORT_ORDER] = order.name
        }
    }

    suspend fun setDenyFallbackNoticeSuppressed(suppressed: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.SUPPRESS_DENY_FALLBACK_NOTICE] = suppressed
        }
    }

    suspend fun setPermissionFlagNoticeSuppressed(suppressed: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.SUPPRESS_PERMISSION_FLAG_NOTICE] = suppressed
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

    suspend fun setSaveIndividualHistory(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.SAVE_INDIVIDUAL_HISTORY] = enabled
        }
    }

    /** Only takes effect while nothing has been chosen, so a choice is never overwritten. */
    suspend fun initializeSavedHistoryOperations(operations: Set<String>) {
        dataStore.edit { preferences ->
            if (preferences[Keys.SAVED_HISTORY_OPERATIONS] == null) {
                preferences[Keys.SAVED_HISTORY_OPERATIONS] = operations
            }
        }
    }

    suspend fun setHistoryOperationSaved(operation: String, saved: Boolean) {
        dataStore.edit { preferences ->
            val current = preferences[Keys.SAVED_HISTORY_OPERATIONS].orEmpty()
            preferences[Keys.SAVED_HISTORY_OPERATIONS] =
                if (saved) current + operation else current - operation
        }
    }

    suspend fun setBatteryNoticeSuppressed(suppressed: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.SUPPRESS_BATTERY_NOTICE] = suppressed
        }
    }

    /** Writes the settings a backup carries and leaves the rest as they are. */
    suspend fun applyBackup(backup: UserSettingsBackup) {
        dataStore.edit { preferences ->
            fun put(key: Preferences.Key<Boolean>, value: Boolean?) {
                if (value != null) preferences[key] = value
            }
            put(Keys.HIDE_SYSTEM_APPS, backup.hideSystemApps)
            put(Keys.SUPPRESS_DENY_FALLBACK_NOTICE, backup.suppressDenyFallbackNotice)
            put(Keys.SUPPRESS_PERMISSION_FLAG_NOTICE, backup.suppressPermissionFlagNotice)
            put(Keys.AUTO_APPLY_NEW_APP_TEMPLATE, backup.autoApplyNewAppTemplate)
            put(Keys.MONITOR_HEADS_UP, backup.monitorHeadsUp)
            put(Keys.SUPPRESS_BATTERY_NOTICE, backup.suppressBatteryNotice)
            put(Keys.SHOW_ALL_MONITOR_OPERATIONS, backup.showAllMonitorOperations)
            put(Keys.SUPPRESS_ALL_OPERATIONS_WARNING, backup.suppressAllOperationsWarning)
            put(Keys.SAVE_INDIVIDUAL_HISTORY, backup.saveIndividualHistory)
            backup.savedHistoryOperations?.let {
                preferences[Keys.SAVED_HISTORY_OPERATIONS] = it
            }
            backup.appSortOrder?.let { preferences[Keys.APP_SORT_ORDER] = it.name }
        }
    }

    private object Keys {
        val SAVE_INDIVIDUAL_HISTORY = booleanPreferencesKey("save_individual_history")
        val SAVED_HISTORY_OPERATIONS = stringSetPreferencesKey("saved_history_operations")
        val HIDE_SYSTEM_APPS = booleanPreferencesKey("hide_system_apps")
        val APP_SORT_ORDER = stringPreferencesKey("app_sort_order")
        val SUPPRESS_DENY_FALLBACK_NOTICE =
            booleanPreferencesKey("suppress_deny_fallback_notice")
        val SUPPRESS_PERMISSION_FLAG_NOTICE =
            booleanPreferencesKey("suppress_permission_flag_notice")
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
