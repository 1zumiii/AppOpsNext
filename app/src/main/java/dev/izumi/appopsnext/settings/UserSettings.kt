package dev.izumi.appopsnext.settings

object UserSettingsDefaults {
    const val HIDE_SYSTEM_APPS = true
    const val SUPPRESS_DENY_FALLBACK_NOTICE = false
    const val AUTO_APPLY_NEW_APP_TEMPLATE = false
    const val BACKGROUND_MONITOR = false
    const val MONITOR_HEADS_UP = false
    const val SUPPRESS_BATTERY_NOTICE = false
    const val SHOW_ALL_MONITOR_OPERATIONS = false
    const val SUPPRESS_ALL_OPERATIONS_WARNING = false
}

data class UserSettings(
    val hideSystemApps: Boolean = UserSettingsDefaults.HIDE_SYSTEM_APPS,
    val suppressDenyFallbackNotice: Boolean =
        UserSettingsDefaults.SUPPRESS_DENY_FALLBACK_NOTICE,
    val autoApplyNewAppTemplate: Boolean =
        UserSettingsDefaults.AUTO_APPLY_NEW_APP_TEMPLATE,
    val backgroundMonitor: Boolean =
        UserSettingsDefaults.BACKGROUND_MONITOR,
    val monitorHeadsUp: Boolean =
        UserSettingsDefaults.MONITOR_HEADS_UP,
    val suppressBatteryNotice: Boolean =
        UserSettingsDefaults.SUPPRESS_BATTERY_NOTICE,
    val showAllMonitorOperations: Boolean =
        UserSettingsDefaults.SHOW_ALL_MONITOR_OPERATIONS,
    val suppressAllOperationsWarning: Boolean =
        UserSettingsDefaults.SUPPRESS_ALL_OPERATIONS_WARNING,
)
