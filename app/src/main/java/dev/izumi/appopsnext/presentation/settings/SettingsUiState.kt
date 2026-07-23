package dev.izumi.appopsnext.presentation.settings

import dev.izumi.appopsnext.settings.AppLanguage

data class SettingsUiState(
    val hideSystemApps: Boolean = false,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
)
