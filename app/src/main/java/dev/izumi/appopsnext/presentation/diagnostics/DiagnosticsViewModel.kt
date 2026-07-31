package dev.izumi.appopsnext.presentation.diagnostics

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.izumi.appopsnext.AppOpsNextApplication
import dev.izumi.appopsnext.appops.AppOpsRepository
import dev.izumi.appopsnext.appops.model.AppOpsReadState
import dev.izumi.appopsnext.diagnostics.DiagnosticEnvironmentCollector
import dev.izumi.appopsnext.diagnostics.DiagnosticReportComposer
import dev.izumi.appopsnext.model.DeviceSummary
import dev.izumi.appopsnext.shizuku.ShizukuController
import dev.izumi.appopsnext.shizuku.model.PrivilegedServiceState
import dev.izumi.appopsnext.shizuku.model.ShizukuState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DiagnosticsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val diagnosticLog =
        getApplication<AppOpsNextApplication>().diagnosticLogRepository
    private val diagnosticEnvironment =
        DiagnosticEnvironmentCollector.collect(application)
    private val shizukuController =
        ShizukuController(application, diagnosticLog)
    private val privilegedServiceClient =
        getApplication<AppOpsNextApplication>().privilegedServiceClient
    private val appOpsRepository = AppOpsRepository(privilegedServiceClient)
    private val appOpsReadState =
        MutableStateFlow<AppOpsReadState>(AppOpsReadState.WaitingForBackend)

    private val device = DeviceSummary(
        manufacturer = diagnosticEnvironment.manufacturer,
        model = diagnosticEnvironment.model,
        androidVersion = diagnosticEnvironment.androidVersion,
        apiLevel = diagnosticEnvironment.apiLevel,
    )

    val uiState = combine(
        shizukuController.state,
        privilegedServiceClient.state,
        appOpsReadState,
        diagnosticLog.lines,
    ) { shizukuState, serviceState, readState, logLines ->
        DiagnosticsUiState(
            device = device,
            shizukuState = shizukuState,
            privilegedServiceState = serviceState,
            appOpsReadState = readState,
            diagnosticReport = DiagnosticReportComposer.compose(
                environment = diagnosticEnvironment,
                shizukuState = shizukuState,
                privilegedServiceState = serviceState,
                appOpsReadState = readState,
                eventLines = logLines,
            ),
            diagnosticEventCount = logLines.size,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = DiagnosticsUiState(device = device),
    )

    init {
        shizukuController.start()
        viewModelScope.launch {
            shizukuController.state.collect { state ->
                if (state is ShizukuState.Ready) {
                    privilegedServiceClient.connect()
                } else {
                    privilegedServiceClient.disconnect()
                }
            }
        }
        viewModelScope.launch {
            privilegedServiceClient.state.collect { state ->
                appOpsReadState.value = if (state is PrivilegedServiceState.Connected) {
                    diagnosticLog.info(
                        source = LOG_SOURCE,
                        message = "Starting AppOps self-check.",
                    )
                    appOpsReadState.value = AppOpsReadState.Reading
                    appOpsRepository.readPackageOps(
                        packageName = application.packageName,
                        uid = application.applicationInfo.uid,
                    ).also { result ->
                        when (result) {
                            is AppOpsReadState.Ready -> {
                                val message =
                                    "AppOps self-check succeeded. " +
                                        "operationCount=" +
                                        result.operationCount
                                Log.i(LOG_SOURCE, message)
                                diagnosticLog.info(
                                    source = LOG_SOURCE,
                                    message = message,
                                )
                            }

                            is AppOpsReadState.Failure ->
                                diagnosticLog.error(
                                    source = LOG_SOURCE,
                                    message =
                                        "AppOps self-check failed. " +
                                            "reason=${result.reason}",
                                )

                            else -> Unit
                        }
                    }
                } else {
                    AppOpsReadState.WaitingForBackend
                }
            }
        }
    }

    fun performShizukuAction() {
        when (shizukuController.state.value) {
            ShizukuState.PermissionRequired,
            ShizukuState.PermissionDenied,
            -> shizukuController.requestPermission()

            else -> shizukuController.refresh()
        }
    }

    fun retryPrivilegedService() {
        privilegedServiceClient.retry()
    }

    fun clearDiagnosticLog() {
        diagnosticLog.clear()
    }

    override fun onCleared() {
        privilegedServiceClient.disconnect()
        shizukuController.stop()
        super.onCleared()
    }

    private companion object {
        const val LOG_SOURCE = "Diagnostics"
    }
}
