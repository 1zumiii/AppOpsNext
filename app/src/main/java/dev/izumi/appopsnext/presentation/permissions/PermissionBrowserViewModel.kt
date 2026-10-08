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
import dev.izumi.appopsnext.appops.parser.OpStatesParser
import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.presentation.app_detail.AppOpModeChangeRequest
import dev.izumi.appopsnext.presentation.app_detail.AppOpModeChangeUiState
import dev.izumi.appopsnext.presentation.app_detail.DenyFallbackModeChangeExecutor
import dev.izumi.appopsnext.presentation.app_detail.ModeChangeAlternativePolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PermissionBrowserViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val app = getApplication<AppOpsNextApplication>()
    private val repository = app.appOpsRepository
    private val executor = AdaptiveScopeModeChangeExecutor(
        writeMemory = app.revokedCompatWriteMemory,
        packagesForUid = ::packagesForUid,
    )
    private val mutableState = MutableStateFlow(PermissionBrowserState())
    val state = mutableState.asStateFlow()
    private val mutableChange =
        MutableStateFlow<AppOpModeChangeUiState>(AppOpModeChangeUiState.Idle)
    val change = mutableChange.asStateFlow()
    private var loadJob: Job? = null
    private var pendingApp: InstalledApp? = null

    fun select(operation: String?) {
        if (mutableChange.value is AppOpModeChangeUiState.Applying) return
        loadJob?.cancel()
        mutableState.value = PermissionBrowserState(operation = operation)
        dismiss()
        if (operation != null) refresh()
    }

    fun refresh() {
        val operation = mutableState.value.operation ?: return
        loadJob?.cancel()
        mutableState.value = mutableState.value.copy(loading = true, failed = false)
        loadJob = viewModelScope.launch {
            try {
                val states = withContext(Dispatchers.IO) {
                    val result = app.privilegedServiceClient.getOpStates(operation)
                    check(!result.timedOut && result.exitCode == 0) {
                        "AppOps query failed"
                    }
                    check(result.stdout.contains("Current AppOps Service state:")) {
                        "Unexpected AppOps dump"
                    }
                    OpStatesParser.parse(
                        output = result.stdout,
                        operationName = operation,
                        userId = Process.myUid() / 100000,
                    )
                }
                mutableState.value = PermissionBrowserState(
                    operation = operation,
                    states = states,
                    runtimeGrants = readRuntimeGrants(operation),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                app.diagnosticLogRepository.error(
                    source = "PermissionBrowser",
                    message = "Unable to load $operation",
                    error = error,
                )
                mutableState.value = PermissionBrowserState(
                    operation = operation,
                    failed = true,
                )
            }
        }
    }

    fun request(appRow: InstalledApp, mode: AppOpMode) {
        if (mutableChange.value !is AppOpModeChangeUiState.Idle) return
        val current = mutableState.value
        if (current.loading || current.failed) return
        val operation = current.operation ?: return
        val effective = current.states?.effective(appRow.uid, appRow.packageName) ?: return
        val originalMode = effective.mode ?: return
        if (originalMode == mode) return
        pendingApp = appRow
        mutableChange.value = AppOpModeChangeUiState.Confirming(
            AppOpModeChangeRequest(
                packageName = appRow.packageName,
                operationName = operation,
                scope = effective.scope,
                originalMode = originalMode,
                requestedMode = mode,
                affectedPackages = affectedPackages(appRow, effective.scope),
                runtimePermissionDenied = isRuntimePermissionDenied(appRow, operation),
            ),
        )
    }

    fun confirm() {
        val request =
            (mutableChange.value as? AppOpModeChangeUiState.Confirming)?.request
                ?: return
        val target = pendingApp ?: return
        mutableChange.value = AppOpModeChangeUiState.Applying(request)
        viewModelScope.launch {
            val operation = AppOpIdentifier(
                stableName = request.operationName,
                shellName = request.operationName,
            )
            var appliedScope = request.scope
            var permissionFlagApplied = false
            var restrictionBlocked = false
            val outcome = repository.withWriteTransaction { transaction ->
                DenyFallbackModeChangeExecutor { mode ->
                    val scopeOutcome = executor.execute(
                        packageName = target.packageName,
                        uid = target.uid,
                        preferredScope = request.scope,
                        requestedMode = mode,
                        readMode = { scope ->
                            repository.readMode(
                                packageName = target.packageName,
                                operation = operation,
                                scope = scope,
                            )
                        },
                        revokedCompatRetry = RevokedCompatRetry(
                            operationName = request.operationName,
                        ) { permission ->
                            transaction.applyRevokedCompat(
                                packageName = target.packageName,
                                uid = target.uid,
                                operation = operation,
                                permissionName = permission,
                                requestedMode = mode,
                            )
                        },
                    ) { scope ->
                        if (scope == request.scope) {
                            transaction.changeMode(
                                packageName = target.packageName,
                                operation = operation,
                                scope = scope,
                                expectedOriginalMode = request.originalMode,
                                requestedMode = mode,
                            )
                        } else {
                            transaction.applyMode(
                                packageName = target.packageName,
                                operation = operation,
                                scope = scope,
                                requestedMode = mode,
                            )
                        }
                    }
                    appliedScope = scopeOutcome.appliedScope
                    permissionFlagApplied = scopeOutcome.permissionFlagApplied
                    restrictionBlocked = scopeOutcome.restrictionBlocked
                    scopeOutcome.result
                }.execute(request.requestedMode)
            }
            val resolvedRequest = request.copy(
                scope = appliedScope,
                affectedPackages = affectedPackages(target, appliedScope),
            )
            val settings = app.userSettingsRepository.settings.first()
            mutableChange.value = when (val result = outcome.result) {
                is AppOpModeChangeResult.Success -> when {
                    permissionFlagApplied &&
                        result.appliedMode == AppOpMode.IGNORE &&
                        !settings.suppressPermissionFlagNotice ->
                        AppOpModeChangeUiState.PermissionFlagApplied(
                            request = resolvedRequest,
                            denyFallbackAttempted = outcome.denyFallbackAttempted,
                        )
                    outcome.denyFallbackAttempted && !settings.suppressDenyFallbackNotice ->
                        AppOpModeChangeUiState.DenyFallbackApplied(resolvedRequest)
                    else -> AppOpModeChangeUiState.Idle
                }
                is AppOpModeChangeResult.Failure -> AppOpModeChangeUiState.Failure(
                    request = resolvedRequest,
                    result = result,
                    denyFallbackAttempted = outcome.denyFallbackAttempted,
                    runtimePermissionManaged = restrictionBlocked,
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
        if (
            !ModeChangeAlternativePolicy.canTryForeground(
                request = failure.request,
                result = failure.result,
            )
        ) {
            return
        }
        mutableChange.value = AppOpModeChangeUiState.Confirming(
            failure.request.copy(requestedMode = AppOpMode.FOREGROUND),
        )
    }

    fun dismissFlagNotice(suppress: Boolean) {
        dismiss()
        if (suppress) {
            viewModelScope.launch {
                app.userSettingsRepository.setPermissionFlagNoticeSuppressed(true)
            }
        }
    }

    fun dismissDenyNotice(suppress: Boolean) {
        dismiss()
        if (suppress) {
            viewModelScope.launch {
                app.userSettingsRepository.setDenyFallbackNoticeSuppressed(true)
            }
        }
    }

    private fun packagesForUid(uid: Int): List<String> =
        app.packageManager.getPackagesForUid(uid)?.toList().orEmpty()

    private fun affectedPackages(target: InstalledApp, scope: AppOpScope): List<String> =
        if (scope == AppOpScope.UID) packagesForUid(target.uid)
        else listOf(target.packageName)

    private fun isRuntimePermissionDenied(target: InstalledApp, operation: String): Boolean {
        val permission = AppOpRuntimePermissionCatalog.requiredPermission(operation) ?: return false
        return app.packageManager.checkPermission(permission, target.packageName) !=
            PackageManager.PERMISSION_GRANTED
    }

    private suspend fun readRuntimeGrants(operation: String): Map<String, Boolean> =
        withContext(Dispatchers.IO) {
            val permission = AppOpRuntimePermissionCatalog.requiredPermission(operation)
                ?: return@withContext emptyMap()
            // PackageManager reads do not issue one AppOps shell command per app.
            val installed = app.installedAppsRepository.loadInstalledApps()
            buildMap {
                installed.forEach { target ->
                    try {
                        put(target.packageName, app.packageManager.checkPermission(
                            permission, target.packageName,
                        ) == PackageManager.PERMISSION_GRANTED)
                    } catch (_: RuntimeException) {
                        // An unavailable grant must not be presented as denied.
                    }
                }
            }
        }
}
