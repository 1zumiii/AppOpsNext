package dev.izumi.appopsnext.presentation.permissions

import android.app.Application
import android.content.pm.PackageManager
import android.os.Process
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.izumi.appopsnext.AppOpsNextApplication
import dev.izumi.appopsnext.appops.AdaptiveScopeModeChangeExecutor
import dev.izumi.appopsnext.appops.AppOpRuntimePermissionCatalog
import dev.izumi.appopsnext.appops.RevokedCompatRetry
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpIdentifier
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.appops.parser.OpStates
import dev.izumi.appopsnext.appops.parser.OpStatesParser
import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.presentation.app_detail.AppOpModeChangeRequest
import dev.izumi.appopsnext.presentation.app_detail.AppOpModeChangeUiState
import dev.izumi.appopsnext.presentation.app_detail.DenyFallbackModeChangeExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PermissionBrowserState(
    val operation: String? = null,
    val states: OpStates? = null,
    val loading: Boolean = false,
    val failed: Boolean = false,
)

class PermissionBrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val app = getApplication<AppOpsNextApplication>()
    private val repository = app.appOpsRepository
    private val executor = AdaptiveScopeModeChangeExecutor {
        app.packageManager.getPackagesForUid(it)?.toList().orEmpty()
    }
    private val mutableState = MutableStateFlow(PermissionBrowserState())
    val state = mutableState.asStateFlow()
    private val mutableChange = MutableStateFlow<AppOpModeChangeUiState>(AppOpModeChangeUiState.Idle)
    val change = mutableChange.asStateFlow()
    private var loadJob: Job? = null
    private var pendingApp: InstalledApp? = null

    fun select(operation: String?) {
        if (mutableChange.value is AppOpModeChangeUiState.Applying) return
        loadJob?.cancel()
        mutableState.value = PermissionBrowserState(operation)
        dismiss()
        if (operation != null) refresh()
    }

    fun refresh() {
        val operation = mutableState.value.operation ?: return
        loadJob?.cancel()
        mutableState.value = PermissionBrowserState(operation, loading = true)
        loadJob = viewModelScope.launch {
            try {
                val states = withContext(Dispatchers.IO) {
                    val result = app.privilegedServiceClient.getOpStates(operation)
                    check(!result.timedOut && result.exitCode == 0) { "AppOps query failed" }
                    check(result.stdout.contains("Current AppOps Service state:")) { "Unexpected AppOps dump" }
                    OpStatesParser.parse(result.stdout, operation, Process.myUid() / 100000)
                }
                mutableState.value = PermissionBrowserState(operation, states)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                app.diagnosticLogRepository.error("PermissionBrowser", "Unable to load $operation", error)
                mutableState.value = PermissionBrowserState(operation, failed = true)
            }
        }
    }

    fun request(appRow: InstalledApp, mode: AppOpMode) {
        if (mutableChange.value !is AppOpModeChangeUiState.Idle) return
        val current = mutableState.value
        val operation = current.operation ?: return
        val effective = current.states?.effective(appRow.uid, appRow.packageName) ?: return
        if (effective.mode == mode) return
        pendingApp = appRow
        val permission = AppOpRuntimePermissionCatalog.requiredPermission(operation)
        mutableChange.value = AppOpModeChangeUiState.Confirming(
            AppOpModeChangeRequest(
                packageName = appRow.packageName,
                operationName = operation,
                scope = effective.scope,
                originalMode = effective.mode,
                requestedMode = mode,
                affectedPackages = if (effective.scope == AppOpScope.UID) {
                    app.packageManager.getPackagesForUid(appRow.uid)?.toList().orEmpty()
                } else listOf(appRow.packageName),
                runtimePermissionDenied = permission != null && app.packageManager
                    .checkPermission(permission, appRow.packageName) != PackageManager.PERMISSION_GRANTED,
            ),
        )
    }

    fun confirm() {
        val request = (mutableChange.value as? AppOpModeChangeUiState.Confirming)?.request ?: return
        val target = pendingApp ?: return
        mutableChange.value = AppOpModeChangeUiState.Applying(request)
        viewModelScope.launch {
            val operation = AppOpIdentifier(request.operationName, request.operationName)
            var permissionFlagApplied = false
            var restrictionBlocked = false
            val outcome = repository.withWriteTransaction { transaction ->
                DenyFallbackModeChangeExecutor { mode ->
                    executor.execute(
                        packageName = target.packageName,
                        uid = target.uid,
                        preferredScope = request.scope,
                        requestedMode = mode,
                        allowScopeFallback = false,
                        readMode = { scope -> repository.readMode(target.packageName, operation, scope) },
                        revokedCompatRetry = RevokedCompatRetry(request.operationName) { permission ->
                            transaction.applyRevokedCompat(target.packageName, target.uid, operation, permission, mode)
                        },
                    ) { scope ->
                        transaction.changeMode(target.packageName, operation, scope, request.originalMode, mode)
                    }.also {
                        permissionFlagApplied = it.permissionFlagApplied
                        restrictionBlocked = it.restrictionBlocked
                    }.result
                }.execute(request.requestedMode)
            }
            val settings = app.userSettingsRepository.settings.first()
            mutableChange.value = when (val result = outcome.result) {
                is AppOpModeChangeResult.Success -> when {
                    permissionFlagApplied && result.appliedMode == AppOpMode.IGNORE && !settings.suppressPermissionFlagNotice ->
                        AppOpModeChangeUiState.PermissionFlagApplied(request, outcome.denyFallbackAttempted)
                    outcome.denyFallbackAttempted && !settings.suppressDenyFallbackNotice ->
                        AppOpModeChangeUiState.DenyFallbackApplied(request)
                    else -> AppOpModeChangeUiState.Idle
                }
                is AppOpModeChangeResult.Failure -> AppOpModeChangeUiState.Failure(
                    request, result, outcome.denyFallbackAttempted, restrictionBlocked,
                )
            }
            refresh()
        }
    }

    fun dismiss() {
        if (mutableChange.value !is AppOpModeChangeUiState.Applying) {
            mutableChange.value = AppOpModeChangeUiState.Idle
        }
    }

    fun requestForeground() {
        val failure = mutableChange.value as? AppOpModeChangeUiState.Failure ?: return
        mutableChange.value = AppOpModeChangeUiState.Confirming(failure.request.copy(requestedMode = AppOpMode.FOREGROUND))
    }

    fun dismissFlagNotice(suppress: Boolean) {
        dismiss()
        if (suppress) viewModelScope.launch { app.userSettingsRepository.setPermissionFlagNoticeSuppressed(true) }
    }

    fun dismissDenyNotice(suppress: Boolean) {
        dismiss()
        if (suppress) viewModelScope.launch { app.userSettingsRepository.setDenyFallbackNoticeSuppressed(true) }
    }
}
