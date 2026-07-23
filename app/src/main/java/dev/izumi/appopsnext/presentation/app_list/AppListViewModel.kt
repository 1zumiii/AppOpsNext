package dev.izumi.appopsnext.presentation.app_list

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.izumi.appopsnext.apps.AppListFilter
import dev.izumi.appopsnext.apps.InstalledAppsRepository
import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.AppOpsNextApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppListViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = InstalledAppsRepository(application)
    private val settingsRepository =
        getApplication<AppOpsNextApplication>().userSettingsRepository
    private val installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val searchQuery = MutableStateFlow("")
    private val isLoading = MutableStateFlow(true)
    private val loadFailed = MutableStateFlow(false)

    val uiState = combine(
        installedApps,
        searchQuery,
        isLoading,
        loadFailed,
        settingsRepository.settings,
    ) { apps, query, loading, failed, settings ->
        val eligibleApps = if (settings.hideSystemApps) {
            apps.filterNot(InstalledApp::isSystemApp)
        } else {
            apps
        }
        AppListUiState(
            searchQuery = query,
            totalAppCount = eligibleApps.size,
            allApps = eligibleApps,
            visibleApps = AppListFilter.apply(
                apps = eligibleApps,
                query = query,
            ),
            isLoading = loading,
            loadFailed = failed,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppListUiState(),
    )

    init {
        refresh()
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun refresh() {
        if (isLoading.value && installedApps.value.isNotEmpty()) return

        viewModelScope.launch {
            isLoading.value = true
            loadFailed.value = false
            runCatching {
                repository.loadInstalledApps()
            }.onSuccess { apps ->
                installedApps.value = apps
            }.onFailure {
                loadFailed.value = true
            }
            isLoading.value = false
        }
    }
}
