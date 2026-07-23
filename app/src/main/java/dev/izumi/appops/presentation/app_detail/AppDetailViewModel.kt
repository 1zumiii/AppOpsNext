package dev.izumi.appops.presentation.app_detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.izumi.appops.AppOpsApplication
import dev.izumi.appops.appops.AppOpsRepository
import dev.izumi.appops.appops.model.PackageOpsLoadResult
import dev.izumi.appops.apps.model.InstalledApp
import dev.izumi.appops.shizuku.model.PrivilegedServiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AppDetailViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val privilegedServiceClient =
        getApplication<AppOpsApplication>().privilegedServiceClient
    private val repository = AppOpsRepository(privilegedServiceClient)
    private val selectedApp = MutableStateFlow<InstalledApp?>(null)
    private val mutableUiState =
        MutableStateFlow<AppDetailUiState>(AppDetailUiState.Idle)
    val uiState: StateFlow<AppDetailUiState> = mutableUiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            privilegedServiceClient.state.collectLatest {
                loadSelectedApp()
            }
        }
    }

    fun selectApp(app: InstalledApp) {
        if (selectedApp.value == app && uiState.value is AppDetailUiState.Ready) {
            return
        }
        selectedApp.value = app
        loadSelectedApp()
    }

    fun refresh() {
        loadSelectedApp()
    }

    private fun loadSelectedApp() {
        loadJob?.cancel()
        val app = selectedApp.value ?: run {
            mutableUiState.value = AppDetailUiState.Idle
            return
        }
        if (privilegedServiceClient.state.value !is PrivilegedServiceState.Connected) {
            mutableUiState.value = AppDetailUiState.WaitingForBackend(app)
            return
        }

        mutableUiState.value = AppDetailUiState.Loading(app)
        loadJob = viewModelScope.launch {
            mutableUiState.value = when (
                val result = repository.loadPackageOps(app.packageName)
            ) {
                is PackageOpsLoadResult.Success -> AppDetailUiState.Ready(
                    app = app,
                    snapshot = result.snapshot,
                )

                is PackageOpsLoadResult.Failure -> AppDetailUiState.Failure(
                    app = app,
                    reason = result.reason,
                )
            }
        }
    }
}
