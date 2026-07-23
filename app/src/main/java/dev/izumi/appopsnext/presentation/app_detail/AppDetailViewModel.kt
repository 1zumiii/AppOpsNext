package dev.izumi.appopsnext.presentation.app_detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.izumi.appopsnext.AppOpsNextApplication
import dev.izumi.appopsnext.appops.AppOpsRepository
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpIdentifier
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.appops.model.AppOpsRestorationStatus
import dev.izumi.appopsnext.appops.model.PackageOpsLoadResult
import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.shizuku.model.PrivilegedServiceState
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
        getApplication<AppOpsNextApplication>().privilegedServiceClient
    private val repository = AppOpsRepository(privilegedServiceClient)
    private val selectedApp = MutableStateFlow<InstalledApp?>(null)
    private val mutableUiState =
        MutableStateFlow<AppDetailUiState>(AppDetailUiState.Idle)
    val uiState: StateFlow<AppDetailUiState> = mutableUiState.asStateFlow()
    private val mutableModeChangeState =
        MutableStateFlow<AppOpModeChangeUiState>(AppOpModeChangeUiState.Idle)
    val modeChangeState: StateFlow<AppOpModeChangeUiState> =
        mutableModeChangeState.asStateFlow()
    private val mutableSearchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = mutableSearchQuery.asStateFlow()
    private var loadJob: Job? = null
    private var modeChangeJob: Job? = null

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
        if (selectedApp.value != app) {
            modeChangeJob?.cancel()
            mutableModeChangeState.value = AppOpModeChangeUiState.Idle
            mutableSearchQuery.value = ""
        }
        selectedApp.value = app
        loadSelectedApp()
    }

    fun refresh() {
        loadSelectedApp()
    }

    fun requestModeChange(
        operationName: String,
        scope: AppOpScope,
        originalMode: AppOpMode,
        requestedMode: AppOpMode,
    ) {
        val app = selectedApp.value ?: return
        if (originalMode == requestedMode) return
        if (mutableModeChangeState.value is AppOpModeChangeUiState.Applying) return

        mutableModeChangeState.value = AppOpModeChangeUiState.Confirming(
            AppOpModeChangeRequest(
                packageName = app.packageName,
                operationName = operationName,
                scope = scope,
                originalMode = originalMode,
                requestedMode = requestedMode,
                affectedPackages = affectedPackages(app, scope),
            ),
        )
    }

    fun updateSearchQuery(query: String) {
        mutableSearchQuery.value = query
    }

    fun confirmModeChange() {
        val request =
            (mutableModeChangeState.value as? AppOpModeChangeUiState.Confirming)
                ?.request
                ?: return
        if (selectedApp.value?.packageName != request.packageName) return

        mutableModeChangeState.value = AppOpModeChangeUiState.Applying(request)
        modeChangeJob = viewModelScope.launch {
            val result = repository.changeMode(
                packageName = request.packageName,
                operation = AppOpIdentifier(
                    stableName = request.operationName,
                    shellName = request.operationName,
                ),
                scope = request.scope,
                expectedOriginalMode = request.originalMode,
                requestedMode = request.requestedMode,
            )

            updateDisplayedMode(request, result)
            mutableModeChangeState.value = when (result) {
                is AppOpModeChangeResult.Success ->
                    AppOpModeChangeUiState.Idle

                is AppOpModeChangeResult.Failure ->
                    AppOpModeChangeUiState.Failure(request, result)
            }
        }
    }

    fun dismissModeChange() {
        if (mutableModeChangeState.value !is AppOpModeChangeUiState.Applying) {
            mutableModeChangeState.value = AppOpModeChangeUiState.Idle
        }
    }

    private fun affectedPackages(
        app: InstalledApp,
        scope: AppOpScope,
    ): List<String> =
        when (scope) {
            AppOpScope.PACKAGE -> listOf(app.packageName)
            AppOpScope.UID ->
                getApplication<AppOpsNextApplication>()
                    .packageManager
                    .getPackagesForUid(app.uid)
                    ?.toList()
                    ?.sorted()
                    ?.ifEmpty { listOf(app.packageName) }
                    ?: listOf(app.packageName)
        }

    private fun updateDisplayedMode(
        request: AppOpModeChangeRequest,
        result: AppOpModeChangeResult,
    ) {
        val mode = when (result) {
            is AppOpModeChangeResult.Success -> result.appliedMode
            is AppOpModeChangeResult.Failure ->
                when (result.restorationStatus) {
                    AppOpsRestorationStatus.NOT_REQUIRED ->
                        result.observedMode ?: result.originalMode

                    AppOpsRestorationStatus.SUCCEEDED -> result.originalMode
                    AppOpsRestorationStatus.FAILED -> null
                }
        } ?: return

        val readyState = mutableUiState.value as? AppDetailUiState.Ready
            ?: return
        if (readyState.app.packageName != request.packageName) return

        mutableUiState.value = readyState.copy(
            snapshot = AppOpSnapshotUpdater.updateMode(
                snapshot = readyState.snapshot,
                operationName = request.operationName,
                scope = request.scope,
                mode = mode,
            ),
        )
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
                val result = repository.loadPackageOps(
                    packageName = app.packageName,
                    uid = app.uid,
                )
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
