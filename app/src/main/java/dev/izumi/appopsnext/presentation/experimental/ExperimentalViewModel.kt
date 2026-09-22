package dev.izumi.appopsnext.presentation.experimental

import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.izumi.appopsnext.AppOpsNextApplication
import dev.izumi.appopsnext.monitor.AppOpsMonitorService
import dev.izumi.appopsnext.monitor.MonitorSelfCheckResult
import dev.izumi.appopsnext.monitor.MonitorStatus
import dev.izumi.appopsnext.monitor.MonitorTarget
import dev.izumi.appopsnext.monitor.MonitorOutcomes
import dev.izumi.appopsnext.monitor.MonitorPointSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

data class ExperimentalUiState(
    val monitorEnabled: Boolean = false,
    val headsUp: Boolean = false,
    val monitorBusy: Boolean = false,
    val selfCheckResult: MonitorSelfCheckResult? = null,
    val status: MonitorStatus? = null,
    val monitorFailure: String? = null,
    val targets: List<MonitorTarget> = emptyList(),
    val points: List<MonitorPointSettings> = emptyList(),
    /** False when vendor battery management may end the process unannounced. */
    val batteryExempt: Boolean = true,
    val batteryNoticeSuppressed: Boolean = false,
    /** Offer every operation the watch can report, not the shortlist. */
    val showAllOperations: Boolean = false,
    val allOperationsWarningSuppressed: Boolean = false,
) {
    val showBatteryNotice: Boolean
        get() = monitorEnabled && !batteryExempt && !batteryNoticeSuppressed

    val targetCount: Int get() = targets.size

    val selectionByPackage: Map<String, Set<String>>
        get() = targets.associate { it.packageName to it.operationNames }

    /**
     * Every monitoring point that can be configured: the watched ones, plus the
     * ones whose settings outlived them.
     */
    val configurablePoints: List<MonitorPointSettings>
        get() {
            val configured = points.associateBy { it.packageName to it.operationName }
            val watched = targets.flatMap { target ->
                target.operationNames.map { target.packageName to it }
            }
            return (watched + configured.keys)
                .distinct()
                .map { key ->
                    configured[key] ?: MonitorPointSettings(key.first, key.second)
                }
        }

    fun settingsFor(packageName: String, operationName: String): MonitorPointSettings =
        points.firstOrNull {
            it.packageName == packageName && it.operationName == operationName
        } ?: MonitorPointSettings(packageName, operationName)

    fun isWatched(packageName: String, operationName: String): Boolean =
        targets.any {
            it.packageName == packageName && operationName in it.operationNames
        }
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
    private val batteryExempt = MutableStateFlow(true)

    val uiState = combine(
        settingsRepository.settings,
        targetsRepository.targets,
        busy,
        selfCheckResult,
        controller.status,
        batteryExempt,
        controller.failure,
        targetsRepository.pointSettings,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val settings = values[0] as dev.izumi.appopsnext.settings.UserSettings
        @Suppress("UNCHECKED_CAST")
        val targets = values[1] as List<MonitorTarget>
        val isBusy = values[2] as Boolean
        val result = values[3] as MonitorSelfCheckResult?
        val status = values[4] as MonitorStatus?
        val exempt = values[5] as Boolean
        @Suppress("UNCHECKED_CAST")
        val pointList = values[7] as List<MonitorPointSettings>
        ExperimentalUiState(
            monitorEnabled = settings.backgroundMonitor,
            headsUp = settings.monitorHeadsUp,
            monitorBusy = isBusy,
            selfCheckResult = result,
            status = status,
            monitorFailure = values[6] as String?,
            targets = targets,
            points = pointList,
            batteryExempt = exempt,
            batteryNoticeSuppressed = settings.suppressBatteryNotice,
            showAllOperations = settings.showAllMonitorOperations,
            allOperationsWarningSuppressed = settings.suppressAllOperationsWarning,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ExperimentalUiState(),
    )

    fun setMonitorEnabled(enabled: Boolean) = changeMonitorEnabled(enabled, allowUnconfirmed = false)

    fun enableUnconfirmedMonitor() {
        if (selfCheckResult.value == MonitorSelfCheckResult.Unconfirmed) {
            changeMonitorEnabled(true, allowUnconfirmed = true)
        }
    }

    /** Store the setting only after confirmation, or an explicit unconfirmed opt-in. */
    private fun changeMonitorEnabled(enabled: Boolean, allowUnconfirmed: Boolean) {
        if (busy.value) return
        if (!enabled) {
            busy.value = true
            selfCheckResult.value = null
            controller.stop()
            viewModelScope.launch {
                try {
                    withContext(NonCancellable) {
                        settingsRepository.setBackgroundMonitor(false)
                        AppOpsMonitorService.stopAndAwait(getApplication())
                    }
                } finally { busy.value = false }
            }
            return
        }
        busy.value = true
        viewModelScope.launch {
            var enabledSuccessfully = false
            try {
                selfCheckResult.value = null
                AppOpsMonitorService.start(getApplication(), checking = true)
                val result = controller.runSelfCheck()
                selfCheckResult.value = result
                if (result is MonitorSelfCheckResult.Passed ||
                    (allowUnconfirmed && result is MonitorSelfCheckResult.Unconfirmed)
                ) {
                    settingsRepository.setBackgroundMonitor(true)
                    AppOpsMonitorService.start(getApplication())
                    enabledSuccessfully = true
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                selfCheckResult.value = MonitorSelfCheckResult.Failed(
                    error.message ?: error::class.java.simpleName,
                )
            } finally {
                if (!enabledSuccessfully) withContext(NonCancellable) {
                    controller.stop(clearFailure = false)
                    settingsRepository.setBackgroundMonitor(false)
                    AppOpsMonitorService.stopAndAwait(getApplication())
                }
                busy.value = false
            }
        }
    }

    /**
     * Re-read whenever the screen resumes, because the user grants the
     * exemption in system settings and comes back to this screen.
     */
    fun refreshBatteryExemption() {
        val application = getApplication<Application>()
        batteryExempt.value = runCatching {
            application.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(application.packageName)
        }.getOrDefault(true)
    }

    fun dismissBatteryNotice() {
        viewModelScope.launch {
            settingsRepository.setBatteryNoticeSuppressed(true)
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

    fun setThrottle(packageName: String, operationName: String, seconds: Int?) {
        viewModelScope.launch {
            targetsRepository.setThrottle(packageName, operationName, seconds)
        }
    }

    fun setPointHeadsUp(packageName: String, operationName: String, headsUp: Boolean?) {
        viewModelScope.launch {
            targetsRepository.setPointHeadsUp(packageName, operationName, headsUp)
        }
    }

    fun setPointOutcomes(
        packageName: String,
        operationName: String,
        outcomes: MonitorOutcomes,
    ) {
        viewModelScope.launch {
            targetsRepository.setPointOutcomes(packageName, operationName, outcomes)
        }
    }

    fun setPointBackgroundOnly(
        packageName: String,
        operationName: String,
        backgroundOnly: Boolean,
    ) {
        viewModelScope.launch {
            targetsRepository.setPointBackgroundOnly(packageName, operationName, backgroundOnly)
        }
    }

    fun setShowAllOperations(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShowAllMonitorOperations(enabled) }
    }

    fun suppressAllOperationsWarning() {
        viewModelScope.launch { settingsRepository.setAllOperationsWarningSuppressed(true) }
    }
}
