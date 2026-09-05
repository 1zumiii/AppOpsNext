package dev.izumi.appopsnext.presentation.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.izumi.appopsnext.AppOpsNextApplication
import dev.izumi.appopsnext.history.AppOpsHistoryRepository
import dev.izumi.appopsnext.history.HistoryPermissionOrdering
import dev.izumi.appopsnext.history.model.AppOpHistoryFailureReason
import dev.izumi.appopsnext.history.model.AppOpHistoryLoadResult
import dev.izumi.appopsnext.history.model.HistoryPermission
import dev.izumi.appopsnext.presentation.app_detail.AppOpDisplayCatalog
import dev.izumi.appopsnext.settings.UserSettingsDefaults
import dev.izumi.appopsnext.shizuku.model.PrivilegedServiceState
import dev.izumi.appopsnext.history.HistoryRefreshController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val app =
        getApplication<AppOpsNextApplication>()
    private val privilegedServiceClient = app.privilegedServiceClient
    private val permissionSettingsRepository =
        app.historyPermissionSettingsRepository
    private val userSettingsRepository = app.userSettingsRepository
    private val historyRepository =
        AppOpsHistoryRepository(privilegedServiceClient)
    private val installedAppsRepository = app.installedAppsRepository
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
    private var hideSystemApps =
        UserSettingsDefaults.HIDE_SYSTEM_APPS
    private val refreshController = HistoryRefreshController(
        scope = viewModelScope,
        intervalMillis = AUTO_REFRESH_INTERVAL_MINUTES * 60_000L,
    ) {
        if (selectedPermissions.isNotEmpty()) {
            try {
                loadSelectedPermissions(selectedPermissions)
            } finally {
                mutableUiState.value = mutableUiState.value.copy(isLoading = false)
            }
        }
    }

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
            userSettingsRepository.settings.collect { settings ->
                if (hideSystemApps != settings.hideSystemApps) {
                    hideSystemApps = settings.hideSystemApps
                    refresh()
                }
            }
        }
        viewModelScope.launch {
            privilegedServiceClient.state.collect { state ->
                refreshController.setConnected(state is PrivilegedServiceState.Connected)
                if (state !is PrivilegedServiceState.Connected) {
                    mutableUiState.value = mutableUiState.value.copy(
                        isLoading = false,
                        waitingForBackend = true,
                    )
                }
            }
        }
    }

    fun setPermissions(operationNames: List<String>) {
        val updatedPermissions =
            HistoryPermissionOrdering.mergeSelection(
                current = selectedPermissions,
                requestedOperationNames = operationNames,
                available = availablePermissions,
            )
        viewModelScope.launch {
            permissionSettingsRepository.setSelected(
                updatedPermissions,
            )
        }
    }

    fun setPermissionOrder(operationNames: List<String>) {
        val reorderedPermissions = HistoryPermissionOrdering.reorder(
            current = selectedPermissions,
            orderedOperationNames = operationNames,
        ) ?: return
        viewModelScope.launch {
            permissionSettingsRepository.setSelected(
                reorderedPermissions,
            )
        }
    }

    fun setVisible(visible: Boolean) = refreshController.setVisible(visible)
    fun setForeground(foreground: Boolean) = refreshController.setForeground(foreground)
    fun refresh() = refreshController.requestRefresh()

    private suspend fun loadSelectedPermissions(
        permissions: List<HistoryPermission>,
    ) {
        val requestedHideSystemApps = hideSystemApps
        mutableUiState.value = mutableUiState.value.copy(
            isLoading = true,
            waitingForBackend = false,
            failureReason = null,
        )

        val apps = runCatching {
            installedAppsRepository.loadInstalledApps()
        }.onFailure { if (it is CancellationException) throw it }.getOrDefault(emptyList())
        val permissionHistories = permissions.map { permission ->
            when (
                val result = historyRepository.loadOperationHistory(
                    permission.shellOperationName,
                )
            ) {
                is AppOpHistoryLoadResult.Success -> {
                    val resolvedEvents = withContext(Dispatchers.Default) {
                        HistoryEventResolver.resolve(
                            events = result.events,
                            installedApps = apps,
                            hideSystemApps = requestedHideSystemApps,
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

        currentCoroutineContext().ensureActive()
        if (permissions != selectedPermissions || requestedHideSystemApps != hideSystemApps) return
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
    }
}
