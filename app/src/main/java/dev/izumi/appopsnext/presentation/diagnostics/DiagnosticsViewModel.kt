package dev.izumi.appopsnext.presentation.diagnostics

import android.app.Application
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.izumi.appopsnext.AppOpsNextApplication
import dev.izumi.appopsnext.appops.AppOpsWatchersRepository
import dev.izumi.appopsnext.appops.model.AppOpsReadState
import dev.izumi.appopsnext.diagnostics.DiagnosticEnvironmentCollector
import dev.izumi.appopsnext.appops.AppOpsReadCapabilities
import dev.izumi.appopsnext.diagnostics.DiagnosticReportComposer
import dev.izumi.appopsnext.diagnostics.ProcessStats
import java.time.ZoneId
import dev.izumi.appopsnext.model.DeviceSummary
import dev.izumi.appopsnext.shizuku.ShizukuController
import dev.izumi.appopsnext.shizuku.model.PrivilegedServiceState
import dev.izumi.appopsnext.shizuku.model.ShizukuState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

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
    private val appOpsRepository = getApplication<AppOpsNextApplication>().appOpsRepository
    private val appOpsReadState =
        MutableStateFlow<AppOpsReadState>(AppOpsReadState.WaitingForBackend)
    private val watcherRegistry = MutableStateFlow("Not read in this session.")
    private val monitorReport = combine(
        watcherRegistry,
        getApplication<AppOpsNextApplication>().appOpsMonitorController.status,
    ) { registry, monitor -> "$registry\nmonitor=$monitor" }
    private val readPaths = combine(
        getApplication<AppOpsNextApplication>().appOpsReadCapabilities.status,
        getApplication<AppOpsNextApplication>().structuredAppOpsReads.enabled,
        AppOpsReadCapabilities::report,
    )
    private val processes = MutableStateFlow("Not read in this session.")
    private val registryReports = combine(monitorReport, readPaths, processes, ::Triple)

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
        registryReports,
    ) { shizukuState, serviceState, readState, logLines, (watchers, reads, processList) ->
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
                watcherRegistry = watchers,
                readPaths = reads,
                processes = processList,
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
                    // One line for the check, written with its result: the result
                    // follows within a moment and its start adds nothing to it.
                    val startedAt = SystemClock.elapsedRealtime()
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
                                        result.operationCount +
                                        ", durationMs=" +
                                        (SystemClock.elapsedRealtime() - startedAt)
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
                                            "reason=${result.reason}, durationMs=" +
                                            (SystemClock.elapsedRealtime() - startedAt),
                                )

                            else -> Unit
                        }
                    }
                } else {
                    watcherRegistry.value = "Backend unavailable; no current snapshot."
                    processes.value = "Backend unavailable; no current snapshot."
                    AppOpsReadState.WaitingForBackend
                }
                if (state is PrivilegedServiceState.Connected) {
                    processes.value = readProcesses()
                    watcherRegistry.value = try {
                        val snapshot = AppOpsWatchersRepository(privilegedServiceClient).read()
                        "capturedAt=${java.time.Instant.now()}, recognized=${snapshot.recognized}, " +
                            "malformedAccessKinds=${snapshot.malformedAccessKinds}, " +
                            "modeRegistrations=${snapshot.modeWatchers.size}\n" +
                            snapshot.accessWatchers.joinToString("\n") +
                            "\nRegistration identity may be shared by other Shizuku clients; delivery is not verified."
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        "Registry unavailable: ${error.message}"
                    }
                }
            }
        }
    }

    /** Reads the process list again, as when the report is opened. */
    fun refreshProcesses() {
        if (privilegedServiceClient.state.value !is PrivilegedServiceState.Connected) return
        viewModelScope.launch { processes.value = readProcesses() }
    }

    /** Never fails the report: a failed read becomes the section's text. */
    private suspend fun readProcesses(): String = try {
        val packageName = getApplication<AppOpsNextApplication>().packageName
        val result = privilegedServiceClient.getProcesses(packageName)
        if (result.timedOut || result.exitCode != 0) {
            "Process snapshot unavailable: exitCode=${result.exitCode}, timedOut=${result.timedOut}, " +
                "stderr=${result.stderr.trim().take(MAX_PROCESS_ERROR_LENGTH)}"
        } else {
            val clockTicks = Os.sysconf(OsConstants._SC_CLK_TCK)
            val now = System.currentTimeMillis()
            ProcessStats.formatSnapshot(
                entries = ProcessStats.decodeRaw(
                    raw = result.stdout,
                    packageName = packageName,
                    clockTicksPerSecond = clockTicks,
                    bootEpochMillis = now - SystemClock.elapsedRealtime(),
                ),
                capturedAtMillis = now,
                clockTicksPerSecond = clockTicks,
                packageName = packageName,
                zone = ZoneId.systemDefault(),
            )
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        "Process snapshot unavailable: ${error::class.java.simpleName}: ${error.message.orEmpty()}"
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
        const val MAX_PROCESS_ERROR_LENGTH = 200
    }
}
