package dev.izumi.appopsnext.presentation.app_list

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.izumi.appopsnext.appops.changes.AppOpChangeRecord
import dev.izumi.appopsnext.appops.changes.AppOpChangeRecords
import dev.izumi.appopsnext.apps.AppListFilter
import dev.izumi.appopsnext.apps.AppSortOrder
import dev.izumi.appopsnext.apps.model.AndroidUser
import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.settings.UserSettings
import dev.izumi.appopsnext.shizuku.model.PrivilegedServiceState
import dev.izumi.appopsnext.AppOpsNextApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
    private val changeLog = getApplication<AppOpsNextApplication>().appOpChangeLog
    private val users = MutableStateFlow<List<AndroidUser>>(emptyList())
    private val installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val searchQuery = MutableStateFlow("")
    private val isLoading = MutableStateFlow(true)
    private val loadFailed = MutableStateFlow(false)
    private var loadJob: Job? = null

    /**
     * Everything but the search, worked out off the main thread. The search
     * stays on the main thread so the text field never shows an older query.
     */
    private val unfilteredState = combine(
        installedApps,
        isLoading,
        loadFailed,
        settingsRepository.settings,
        users,
        userApps.selectedUserId,
        changeLog.records,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val apps = values[0] as List<InstalledApp>
        val loading = values[1] as Boolean
        val failed = values[2] as Boolean
        val settings = values[3] as UserSettings
        @Suppress("UNCHECKED_CAST")
        val allUsers = values[4] as List<AndroidUser>
        val selectedUserId = values[5] as Int?
        @Suppress("UNCHECKED_CAST")
        val changes = values[6] as List<AppOpChangeRecord>
        val filteredApps = if (settings.hideSystemApps) {
            apps.filterNot(InstalledApp::isSystemApp)
        } else {
            apps
        }
        val sortOrder = settings.appSortOrder
        val changesByPackage = if (sortOrder == AppSortOrder.CHANGED_ONLY) {
            AppOpChangeRecords.byPackage(changes)
        } else {
            emptyMap()
        }
        val eligibleApps = sortOrder.sort(filteredApps) { app ->
            val userId = app.userId ?: userApps.currentUserId
            AppOpChangeRecords.latestTime(
                records = changesByPackage[userId to app.packageName].orEmpty(),
                userId = userId,
                packageName = app.packageName,
                firstInstallTimeMillis = app.firstInstallTimeMillis,
            )
        }
        AppListUiState(
            totalAppCount = apps.size,
            allApps = eligibleApps,
            isLoading = loading,
            loadFailed = failed,
            users = allUsers,
            selectedUserId = selectedUserId,
            currentUserId = userApps.currentUserId,
            sortOrder = sortOrder,
            changes = changes,
        )
    }.flowOn(Dispatchers.Default)

    val uiState = combine(unfilteredState, searchQuery) { state, query ->
        state.copy(
            searchQuery = query,
            visibleApps = AppListFilter.apply(
                apps = state.allApps,
                query = query,
            ),
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

    fun setSortOrder(order: AppSortOrder) {
        viewModelScope.launch { settingsRepository.setAppSortOrder(order) }
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun refresh() = load(forceRefresh = true, holdIndicator = true)

    /**
     * Installed packages rarely change while the user is away, so a resume reuses
     * the repository cache instead of enumerating and labelling every package again.
     */
    /** Quiet: the list stays as it is and no indicator shows while it reloads. */
    fun refreshAfterResume() {
        if (installedApps.value.isNotEmpty()) {
            load(forceRefresh = false, holdIndicator = false, quiet = true)
        }
    }

    /**
     * A pull-to-refresh indicator hides only when it sees loading change. A
     * cached reload can finish within one frame, so a user refresh keeps the
     * loading state visible for a moment.
     */
    private fun load(forceRefresh: Boolean, holdIndicator: Boolean, quiet: Boolean = false) {
        if (loadJob?.isActive == true) return

        loadJob = viewModelScope.launch {
            if (!quiet) isLoading.value = true
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
                pruneChanges(userApps.selectedUserId.value, apps)
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
            if (!quiet) isLoading.value = false
        }
    }

    /**
     * Drops the change records of packages that are gone or were installed
     * again. An empty list is more likely a failed read than an empty user.
     */
    private suspend fun pruneChanges(userId: Int?, apps: List<InstalledApp>) {
        if (apps.isEmpty()) return
        runCatching {
            changeLog.prune(userId, apps.associate { it.packageName to it.firstInstallTimeMillis })
        }.onFailure { if (it is CancellationException) throw it }
    }

    private companion object {
        const val LOG_SOURCE = "AppList"
        const val MIN_REFRESH_INDICATOR_MILLIS = 400L
    }
}
