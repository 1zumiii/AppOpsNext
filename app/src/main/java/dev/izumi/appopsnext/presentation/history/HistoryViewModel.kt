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
import dev.izumi.appopsnext.history.HistorySnapshot
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
    private val snapshotStore = app.historySnapshotStore
    private var snapshots = emptyMap<String, HistorySnapshot>()
    private var failures = emptyMap<String, AppOpHistoryFailureReason>()
    private var forceRefreshRequested = false
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
            snapshots = snapshotStore.read()
            permissionSettingsRepository.selectedPermissions.collect {
                selectedPermissions = it
                publishSnapshots()
                refreshController.requestRefresh()
            }
        }
        viewModelScope.launch {
            userSettingsRepository.settings.collect { settings ->
                if (hideSystemApps != settings.hideSystemApps) {
                    hideSystemApps = settings.hideSystemApps
                    publishSnapshots()
                    refreshController.requestRefresh()
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
    fun refresh() {
        forceRefreshRequested = true
        refreshController.requestRefresh()
    }

    private fun publishSnapshots() {
        val histories = HistorySnapshotPresentation.resolve(
            selectedPermissions, snapshots, failures, hideSystemApps,
        )
        val failed = histories.mapNotNull(PermissionHistory::failureReason)
        mutableUiState.value = mutableUiState.value.copy(
            permissions = histories,
            failureReason = failed.firstOrNull().takeIf { failed.size == histories.size },
            partialFailureCount = failed.size.takeIf { it < histories.size } ?: 0,
            lastUpdatedAtMillis = histories.mapNotNull { it.lastUpdatedAtMillis }.minOrNull(),
        )
    }

    private suspend fun loadSelectedPermissions(
        permissions: List<HistoryPermission>,
    ) {
        val force = forceRefreshRequested
        forceRefreshRequested = false
        val now = System.currentTimeMillis()
        val pending = permissions.filter { permission ->
            force || !HistorySnapshotFreshness.isFresh(
                snapshots[permission.shellOperationName]?.fetchedAtMillis,
                now,
                AUTO_REFRESH_INTERVAL_MINUTES * 60_000L,
            )
        }
        mutableUiState.value = mutableUiState.value.copy(waitingForBackend = false)
        if (pending.isEmpty()) return
        mutableUiState.value = mutableUiState.value.copy(isLoading = true)

        // Metadata failure must not turn real history into a successful empty snapshot.
        val apps = try {
            installedAppsRepository.loadInstalledApps()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failures = failures + pending.associate {
                it.shellOperationName to AppOpHistoryFailureReason.BACKEND_UNAVAILABLE
            }
            publishSnapshots()
            return
        }
        for (permission in pending) {
            currentCoroutineContext().ensureActive()
            val operation = permission.shellOperationName
            when (val result = historyRepository.loadOperationHistory(operation)) {
                is AppOpHistoryLoadResult.Success -> {
                    val resolved = withContext(Dispatchers.Default) {
                        HistoryEventResolver.resolve(result.events, apps, hideSystemApps = false)
                    }
                    val snapshot = HistorySnapshot(resolved, System.currentTimeMillis())
                    snapshotStore.put(operation, snapshot)
                    snapshots = snapshots + (operation to snapshot)
                    failures = failures - operation
                }
                is AppOpHistoryLoadResult.Failure -> {
                    failures = failures + (operation to result.reason)
                }
            }
            currentCoroutineContext().ensureActive()
            publishSnapshots()
        }
    }

    private companion object {
        const val AUTO_REFRESH_INTERVAL_MINUTES = 5
    }
}
