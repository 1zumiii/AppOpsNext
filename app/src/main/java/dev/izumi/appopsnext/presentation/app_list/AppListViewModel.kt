package dev.izumi.appopsnext.presentation.app_list

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.izumi.appopsnext.apps.AppListFilter
import dev.izumi.appopsnext.apps.model.AndroidUser
import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.settings.UserSettings
import dev.izumi.appopsnext.shizuku.model.PrivilegedServiceState
import dev.izumi.appopsnext.AppOpsNextApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AppListViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = getApplication<AppOpsNextApplication>().installedAppsRepository
    private val settingsRepository =
        getApplication<AppOpsNextApplication>().userSettingsRepository
    private val diagnosticLog =
        getApplication<AppOpsNextApplication>().diagnosticLogRepository
    private val userApps = getApplication<AppOpsNextApplication>().userAppsRepository
    private val privilegedServiceClient =
        getApplication<AppOpsNextApplication>().privilegedServiceClient
    private val users = MutableStateFlow<List<AndroidUser>>(emptyList())
    private val installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val searchQuery = MutableStateFlow("")
    private val isLoading = MutableStateFlow(true)
    private val loadFailed = MutableStateFlow(false)
    private var loadJob: Job? = null

    val uiState = combine(
        installedApps,
        searchQuery,
        isLoading,
        loadFailed,
        settingsRepository.settings,
        users,
        userApps.selectedUserId,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val apps = values[0] as List<InstalledApp>
        val query = values[1] as String
        val loading = values[2] as Boolean
        val failed = values[3] as Boolean
        val settings = values[4] as UserSettings
        @Suppress("UNCHECKED_CAST")
        val allUsers = values[5] as List<AndroidUser>
        val selectedUserId = values[6] as Int?
        val eligibleApps = if (settings.hideSystemApps) {
            apps.filterNot(InstalledApp::isSystemApp)
        } else {
            apps
        }
        AppListUiState(
            searchQuery = query,
            totalAppCount = apps.size,
            allApps = eligibleApps,
            visibleApps = AppListFilter.apply(
                apps = eligibleApps,
                query = query,
            ),
            isLoading = loading,
            loadFailed = failed,
            users = allUsers,
            selectedUserId = selectedUserId,
            currentUserId = userApps.currentUserId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppListUiState(),
    )

    init {
        load(forceRefresh = true, holdIndicator = false)
        // Other users are listed by the privileged backend, so the picker
        // appears once it connects.
        viewModelScope.launch {
            privilegedServiceClient.state.collect { state ->
                if (state is PrivilegedServiceState.Connected) loadUsers()
            }
        }
    }

    fun selectUser(userId: Int?) {
        val target = userId?.takeIf { it != userApps.currentUserId }
        if (target == userApps.selectedUserId.value) return
        userApps.select(target)
        searchQuery.value = ""
        installedApps.value = emptyList()
        loadJob?.cancel()
        loadJob = null
        load(forceRefresh = true, holdIndicator = false)
    }

    private suspend fun loadUsers() {
        val loaded = userApps.loadUsers()
        users.value = loaded
        // A user that disappeared, such as a removed clone, falls back to the owner.
        val selected = userApps.selectedUserId.value
        if (selected != null && loaded.none { it.id == selected }) selectUser(null)
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun refresh() = load(forceRefresh = true, holdIndicator = true)

    /**
     * Installed packages rarely change while the user is away, so a resume reuses
     * the repository cache instead of enumerating and labelling every package again.
     */
    fun refreshAfterResume() {
        if (installedApps.value.isNotEmpty()) {
            load(forceRefresh = false, holdIndicator = false)
        }
    }

    /**
     * A pull-to-refresh indicator hides only when it sees loading change. A
     * cached reload can finish within one frame, so a user refresh keeps the
     * loading state visible for a moment.
     */
    private fun load(forceRefresh: Boolean, holdIndicator: Boolean) {
        if (loadJob?.isActive == true) return

        loadJob = viewModelScope.launch {
            isLoading.value = true
            loadFailed.value = false
            val startedAt = SystemClock.elapsedRealtime()
            diagnosticLog.info(
                source = LOG_SOURCE,
                message = "Loading installed applications.",
            )
            runCatching {
                val selected = userApps.selectedUserId.value
                if (selected == null) {
                    repository.loadInstalledApps(forceRefresh = forceRefresh)
                } else {
                    userApps.loadApps(selected)
                }
            }.onSuccess { apps ->
                installedApps.value = apps
                diagnosticLog.info(
                    source = LOG_SOURCE,
                    message =
                        "Installed applications loaded. count=${apps.size}, " +
                            "durationMs=" +
                            (SystemClock.elapsedRealtime() - startedAt),
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
                loadFailed.value = true
                diagnosticLog.error(
                    source = LOG_SOURCE,
                    message =
                        "Unable to load installed applications. durationMs=" +
                            (SystemClock.elapsedRealtime() - startedAt),
                    error = error,
                )
            }
            if (holdIndicator) {
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                delay((MIN_REFRESH_INDICATOR_MILLIS - elapsed).coerceAtLeast(0))
            }
            isLoading.value = false
        }
    }

    private companion object {
        const val LOG_SOURCE = "AppList"
        const val MIN_REFRESH_INDICATOR_MILLIS = 400L
    }
}
