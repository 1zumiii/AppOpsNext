package dev.izumi.appopsnext.presentation.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.izumi.appopsnext.AppOpsNextApplication
import dev.izumi.appopsnext.apps.InstalledAppsRepository
import dev.izumi.appopsnext.history.AppOpsHistoryRepository
import dev.izumi.appopsnext.history.model.AppOpHistoryFailureReason
import dev.izumi.appopsnext.history.model.AppOpHistoryLoadResult
import dev.izumi.appopsnext.history.model.TrackedHistoryPermission
import dev.izumi.appopsnext.shizuku.model.PrivilegedServiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val privilegedServiceClient =
        getApplication<AppOpsNextApplication>().privilegedServiceClient
    private val historyRepository =
        AppOpsHistoryRepository(privilegedServiceClient)
    private val installedAppsRepository = InstalledAppsRepository(application)
    private val mutableUiState = MutableStateFlow(HistoryUiState())
    private var refreshInProgress = false

    val uiState: StateFlow<HistoryUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            privilegedServiceClient.state.collect { state ->
                if (state is PrivilegedServiceState.Connected) {
                    refresh()
                } else {
                    mutableUiState.value = HistoryUiState(
                        waitingForBackend = true,
                    )
                }
            }
        }
    }

    fun refresh() {
        if (
            refreshInProgress ||
            privilegedServiceClient.state.value
                !is PrivilegedServiceState.Connected
        ) {
            return
        }

        viewModelScope.launch {
            refreshInProgress = true
            try {
                mutableUiState.value = mutableUiState.value.copy(
                    isLoading = true,
                    waitingForBackend = false,
                    failureReason = null,
                )

                val apps = runCatching {
                    installedAppsRepository.loadInstalledApps()
                }.getOrDefault(emptyList())
                val appsByPackage = apps.associateBy { it.packageName }
                val permissionHistories = mutableListOf<PermissionHistory>()
                val failures = mutableListOf<AppOpHistoryFailureReason>()

                TrackedHistoryPermission.entries.forEach { permission ->
                    when (
                        val result = historyRepository.loadOperationHistory(
                            permission.shellOperationName,
                        )
                    ) {
                        is AppOpHistoryLoadResult.Success -> {
                            val resolvedEvents =
                                result.events.mapNotNull { event ->
                                    val app = appsByPackage[event.packageName]
                                        ?: return@mapNotNull null
                                    if (app.uid != event.uid) {
                                        return@mapNotNull null
                                    }
                                    ResolvedHistoryEvent(
                                        event = event,
                                        app = app,
                                    )
                                }
                            permissionHistories += PermissionHistory(
                                permission = permission,
                                events = resolvedEvents,
                            )
                        }

                        is AppOpHistoryLoadResult.Failure ->
                            failures += result.reason
                    }
                }

                mutableUiState.value = HistoryUiState(
                    isLoading = false,
                    waitingForBackend = false,
                    permissions = permissionHistories,
                    failureReason = failures.firstOrNull()
                        .takeIf { permissionHistories.isEmpty() },
                    partialFailureCount = failures.size
                        .takeIf { permissionHistories.isNotEmpty() }
                        ?: 0,
                )
            } finally {
                refreshInProgress = false
            }
        }
    }
}
