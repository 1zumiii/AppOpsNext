package dev.izumi.appopsnext.presentation.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.izumi.appopsnext.AppOpsNextApplication
import dev.izumi.appopsnext.settings.AppLanguage
import dev.izumi.appopsnext.settings.ApplicationLanguageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository =
        getApplication<AppOpsNextApplication>().userSettingsRepository
    private val languageManager = ApplicationLanguageManager(application)
    private val appLanguage =
        MutableStateFlow(languageManager.currentLanguage())

    val uiState = combine(
        repository.settings,
        appLanguage,
    ) { settings, language ->
            SettingsUiState(
                hideSystemApps = settings.hideSystemApps,
                appLanguage = language,
            )
    }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsUiState(),
        )

    fun setHideSystemApps(hidden: Boolean) {
        viewModelScope.launch {
            repository.setHideSystemApps(hidden)
        }
    }

    fun setAppLanguage(language: AppLanguage) {
        if (appLanguage.value == language) return
        appLanguage.value = language
        languageManager.setLanguage(language)
    }
}
