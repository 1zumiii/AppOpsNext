package dev.izumi.appopsnext.presentation.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.izumi.appopsnext.AppOpsNextApplication
import dev.izumi.appopsnext.settings.AppLanguage
import dev.izumi.appopsnext.settings.ApplicationLanguageManager
import dev.izumi.appopsnext.update.UpdateChecker
import dev.izumi.appopsnext.update.UpdateState
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
    private val updateChecker = UpdateChecker()
    private val updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val uiState = combine(
        repository.settings,
        appLanguage,
        updateState,
    ) { settings, language, update ->
            SettingsUiState(
                hideSystemApps = settings.hideSystemApps,
                appLanguage = language,
                updateState = update,
            )
    }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsUiState(),
        )

    init {
        checkForUpdate()
    }

    /**
     * Runs once when the activity is created and on demand from the version row.
     * The result is never surfaced as a notification or a dialog.
     */
    fun checkForUpdate() {
        if (updateState.value == UpdateState.Checking) return
        viewModelScope.launch {
            updateState.value = UpdateState.Checking
            updateState.value = updateChecker.check()
        }
    }

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
