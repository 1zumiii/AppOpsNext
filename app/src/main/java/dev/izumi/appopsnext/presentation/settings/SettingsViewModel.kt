package dev.izumi.appopsnext.presentation.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.izumi.appopsnext.AppOpsNextApplication
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository =
        getApplication<AppOpsNextApplication>().userSettingsRepository

    val uiState = repository.settings
        .map { settings ->
            SettingsUiState(
                hideSystemApps = settings.hideSystemApps,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsUiState(),
        )

    fun setHideSystemApps(hidden: Boolean) {
        viewModelScope.launch {
            repository.setHideSystemApps(hidden)
        }
    }
}
