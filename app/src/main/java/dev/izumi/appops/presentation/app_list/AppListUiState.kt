package dev.izumi.appops.presentation.app_list

import dev.izumi.appops.apps.model.InstalledApp

data class AppListUiState(
    val searchQuery: String = "",
    val totalAppCount: Int = 0,
    val visibleApps: List<InstalledApp> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
)
