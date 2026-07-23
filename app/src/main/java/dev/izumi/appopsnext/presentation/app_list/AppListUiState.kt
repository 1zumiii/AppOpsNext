package dev.izumi.appopsnext.presentation.app_list

import dev.izumi.appopsnext.apps.model.InstalledApp

data class AppListUiState(
    val searchQuery: String = "",
    val totalAppCount: Int = 0,
    val allApps: List<InstalledApp> = emptyList(),
    val visibleApps: List<InstalledApp> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
)
