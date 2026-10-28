package dev.izumi.appopsnext.presentation.app_list

import dev.izumi.appopsnext.apps.model.AndroidUser
import dev.izumi.appopsnext.apps.model.InstalledApp

data class AppListUiState(
    val searchQuery: String = "",
    val totalAppCount: Int = 0,
    val allApps: List<InstalledApp> = emptyList(),
    val visibleApps: List<InstalledApp> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    /** Every user on the device. The picker shows only when there is more than one. */
    val users: List<AndroidUser> = emptyList(),
    /** Null while the user this app runs in is selected. */
    val selectedUserId: Int? = null,
    val currentUserId: Int = 0,
)
