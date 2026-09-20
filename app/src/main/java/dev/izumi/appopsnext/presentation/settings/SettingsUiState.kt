package dev.izumi.appopsnext.presentation.settings

import dev.izumi.appopsnext.settings.AppLanguage
import dev.izumi.appopsnext.update.UpdateState
import dev.izumi.appopsnext.settings.UserSettingsDefaults

data class SettingsUiState(
    val hideSystemApps: Boolean = UserSettingsDefaults.HIDE_SYSTEM_APPS,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val updateState: UpdateState = UpdateState.Idle,
)
