package dev.izumi.appopsnext.settings

object UserSettingsDefaults {
    const val HIDE_SYSTEM_APPS = true
}

data class UserSettings(
    val hideSystemApps: Boolean = UserSettingsDefaults.HIDE_SYSTEM_APPS,
)
