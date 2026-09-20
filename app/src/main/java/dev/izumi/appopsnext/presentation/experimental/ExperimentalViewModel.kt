package dev.izumi.appopsnext.presentation.experimental

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.izumi.appopsnext.AppOpsNextApplication
import dev.izumi.appopsnext.monitor.AppOpsMonitorService
import dev.izumi.appopsnext.monitor.MonitorSelfCheckResult
import dev.izumi.appopsnext.monitor.MonitorStatus
import dev.izumi.appopsnext.monitor.MonitorTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExperimentalUiState(
    val monitorEnabled: Boolean = false,
    val headsUp: Boolean = false,
    val monitorBusy: Boolean = false,
    val selfCheckResult: MonitorSelfCheckResult? = null,
    val status: MonitorStatus? = null,
    val targets: List<MonitorTarget> = emptyList(),
) {
    val targetCount: Int get() = targets.size

    val selectionByPackage: Map<String, Set<String>>
        get() = targets.associate { it.packageName to it.operationNames }
}

class ExperimentalViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val app = getApplication<AppOpsNextApplication>()
    private val settingsRepository = app.userSettingsRepository
    private val targetsRepository = app.monitorTargetsRepository
    private val controller = app.appOpsMonitorController
    private val busy = MutableStateFlow(false)
    private val selfCheckResult = MutableStateFlow<MonitorSelfCheckResult?>(null)

    val uiState = combine(
        settingsRepository.settings,
        targetsRepository.targets,
        busy,
        selfCheckResult,
        controller.status,
    ) { settings, targets, isBusy, result, status ->
        ExperimentalUiState(
            monitorEnabled = settings.backgroundMonitor,
            headsUp = settings.monitorHeadsUp,
            monitorBusy = isBusy,
            selfCheckResult = result,
            status = status,
            targets = targets,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ExperimentalUiState(),
    )

    /**
     * The self-check runs before the setting is stored, so a device that cannot
     * deliver the callbacks never ends up with the switch left on.
     */
    fun setMonitorEnabled(enabled: Boolean) {
        if (busy.value) return
        if (!enabled) {
            selfCheckResult.value = null
            AppOpsMonitorService.stop(getApplication())
            viewModelScope.launch { settingsRepository.setBackgroundMonitor(false) }
            return
        }
        viewModelScope.launch {
            busy.value = true
            selfCheckResult.value = null
            val result = runCatching { controller.runSelfCheck() }
                .getOrElse { error ->
                    MonitorSelfCheckResult.Failed(
                        error.message ?: error::class.java.simpleName,
                    )
                }
            selfCheckResult.value = result
            if (result is MonitorSelfCheckResult.Passed) {
                settingsRepository.setBackgroundMonitor(true)
                AppOpsMonitorService.start(getApplication())
            } else {
                controller.stop()
                settingsRepository.setBackgroundMonitor(false)
            }
            busy.value = false
        }
    }

    fun setHeadsUp(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setMonitorHeadsUp(enabled) }
    }

    fun setOperations(packageName: String, operationNames: Set<String>) {
        viewModelScope.launch {
            targetsRepository.setOperations(packageName, operationNames)
        }
    }
}
