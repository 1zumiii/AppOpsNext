package dev.izumi.appopsnext.settings

object UserSettingsDefaults {
    const val HIDE_SYSTEM_APPS = true
    const val SUPPRESS_DENY_FALLBACK_NOTICE = false
}

data class UserSettings(
    val hideSystemApps: Boolean = UserSettingsDefaults.HIDE_SYSTEM_APPS,
    val suppressDenyFallbackNotice: Boolean =
        UserSettingsDefaults.SUPPRESS_DENY_FALLBACK_NOTICE,
)
