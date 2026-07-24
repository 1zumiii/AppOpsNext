package dev.izumi.appopsnext.presentation.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.izumi.appopsnext.AppOpsNextApplication
import dev.izumi.appopsnext.apps.InstalledAppsRepository
import dev.izumi.appopsnext.history.AppOpsHistoryRepository
import dev.izumi.appopsnext.history.model.AppOpHistoryFailureReason
import dev.izumi.appopsnext.history.model.AppOpHistoryLoadResult
import dev.izumi.appopsnext.history.model.HistoryPermission
import dev.izumi.appopsnext.presentation.app_detail.AppOpDisplayCatalog
import dev.izumi.appopsnext.shizuku.model.PrivilegedServiceState
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HistoryViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val app =
        getApplication<AppOpsNextApplication>()
    private val privilegedServiceClient = app.privilegedServiceClient
    private val permissionSettingsRepository =
        app.historyPermissionSettingsRepository
    private val historyRepository =
        AppOpsHistoryRepository(privilegedServiceClient)
    private val installedAppsRepository = InstalledAppsRepository(application)
    private val availablePermissions =
        AppOpDisplayCatalog.knownOperations().map {
            HistoryPermission(it.shellName)
        }
    private val mutableUiState = MutableStateFlow(
        HistoryUiState(
            availablePermissions = availablePermissions,
            autoRefreshIntervalMinutes = AUTO_REFRESH_INTERVAL_MINUTES,
        ),
    )
    private var selectedPermissions = emptyList<HistoryPermission>()
    private var refreshInProgress = false
    private var refreshPending = false

    val uiState: StateFlow<HistoryUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            permissionSettingsRepository.selectedPermissions.collect {
                selectedPermissions = it
                val previousByOperation =
                    mutableUiState.value.permissions.associateBy {
                        history -> history.permission.shellOperationName
                    }
                mutableUiState.value = mutableUiState.value.copy(
                    permissions = it.map { permission ->
                        previousByOperation[permission.shellOperationName]
                            ?: PermissionHistory(
                                permission = permission,
                                events = emptyList(),
                            )
                    },
                )
                refresh()
            }
        }
        viewModelScope.launch {
            privilegedServiceClient.state.collect { state ->
                if (state is PrivilegedServiceState.Connected) {
                    refresh()
                } else {
                    mutableUiState.value = mutableUiState.value.copy(
                        isLoading = false,
                        waitingForBackend = true,
                    )
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(AUTO_REFRESH_INTERVAL)
                refresh()
            }
        }
    }

    fun addPermission(operationName: String) {
        viewModelScope.launch {
            permissionSettingsRepository.add(operationName)
        }
    }

    fun removePermission(operationName: String) {
        viewModelScope.launch {
            permissionSettingsRepository.remove(operationName)
        }
    }

    fun refresh() {
        if (
            selectedPermissions.isEmpty() ||
            privilegedServiceClient.state.value
                !is PrivilegedServiceState.Connected
        ) {
            return
        }
        if (refreshInProgress) {
            refreshPending = true
            return
        }

        refreshInProgress = true
        viewModelScope.launch {
            try {
                loadSelectedPermissions(selectedPermissions)
            } finally {
                refreshInProgress = false
                if (refreshPending) {
                    refreshPending = false
                    refresh()
                }
            }
        }
    }

    private suspend fun loadSelectedPermissions(
        permissions: List<HistoryPermission>,
    ) {
        mutableUiState.value = mutableUiState.value.copy(
            isLoading = true,
            waitingForBackend = false,
            failureReason = null,
        )

        val apps = runCatching {
            installedAppsRepository.loadInstalledApps()
        }.getOrDefault(emptyList())
        val appsByPackage = apps.associateBy { it.packageName }
        val permissionHistories = permissions.map { permission ->
            when (
                val result = historyRepository.loadOperationHistory(
                    permission.shellOperationName,
                )
            ) {
                is AppOpHistoryLoadResult.Success -> {
                    val resolvedEvents =
                        result.events.mapNotNull { event ->
                            val installedApp =
                                appsByPackage[event.packageName]
                                    ?: return@mapNotNull null
                            if (installedApp.uid != event.uid) {
                                return@mapNotNull null
                            }
                            ResolvedHistoryEvent(
                                event = event,
                                app = installedApp,
                            )
                        }
                    PermissionHistory(
                        permission = permission,
                        events = resolvedEvents,
                    )
                }

                is AppOpHistoryLoadResult.Failure -> PermissionHistory(
                    permission = permission,
                    events = emptyList(),
                    failureReason = result.reason,
                )
            }
        }
        val failures = permissionHistories.mapNotNull(
            PermissionHistory::failureReason,
        )

        mutableUiState.value = HistoryUiState(
            isLoading = false,
            waitingForBackend = false,
            permissions = permissionHistories,
            availablePermissions = availablePermissions,
            failureReason = failures.firstOrNull()
                .takeIf { failures.size == permissionHistories.size },
            partialFailureCount = failures.size
                .takeIf { failures.size < permissionHistories.size }
                ?: 0,
            lastUpdatedAtMillis = System.currentTimeMillis(),
            autoRefreshIntervalMinutes = AUTO_REFRESH_INTERVAL_MINUTES,
        )
    }

    private companion object {
        const val AUTO_REFRESH_INTERVAL_MINUTES = 5
        val AUTO_REFRESH_INTERVAL = AUTO_REFRESH_INTERVAL_MINUTES.minutes
    }
}
