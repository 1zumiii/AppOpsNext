package dev.izumi.appops.presentation.app_list

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.izumi.appops.apps.AppListFilter
import dev.izumi.appops.apps.InstalledAppsRepository
import dev.izumi.appops.apps.model.InstalledApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppListViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = InstalledAppsRepository(application)
    private val installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val searchQuery = MutableStateFlow("")
    private val isLoading = MutableStateFlow(true)
    private val loadFailed = MutableStateFlow(false)

    val uiState = combine(
        installedApps,
        searchQuery,
        isLoading,
        loadFailed,
    ) { apps, query, loading, failed ->
        AppListUiState(
            searchQuery = query,
            totalAppCount = apps.size,
            visibleApps = AppListFilter.apply(apps, query),
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
