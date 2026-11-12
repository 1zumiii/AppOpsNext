package dev.izumi.appopsnext.presentation.experimental

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.izumi.appopsnext.AppOpsNextApplication
import dev.izumi.appopsnext.monitor.AppOpCodes
import dev.izumi.appopsnext.monitor.BuiltInMonitorTemplates
import dev.izumi.appopsnext.monitor.MonitorOutcomes
import dev.izumi.appopsnext.monitor.MonitorTemplate
import dev.izumi.appopsnext.monitor.MonitorTemplateRule
import dev.izumi.appopsnext.monitor.MonitorThrottles
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What became of applying a template, shown once and then dismissed. */
sealed interface MonitorTemplateApplyOutcome {
    data class Applied(
        val appCount: Int,
        val operations: List<String>,
        val skippedOperations: List<String>,
    ) : MonitorTemplateApplyOutcome

    /** The monitor was switched on before the template could be applied. */
    data object Locked : MonitorTemplateApplyOutcome
}

class MonitorTemplatesViewModel(application: Application) : AndroidViewModel(application) {
    private val app = getApplication<AppOpsNextApplication>()
    private val repository = app.monitorTargetsRepository
    private val settingsRepository = app.userSettingsRepository

    /** The user's own templates; the built-in ones come from [BuiltInMonitorTemplates]. */
    val templates: StateFlow<List<MonitorTemplate>> = repository.templates
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val mutableApplyOutcome = MutableStateFlow<MonitorTemplateApplyOutcome?>(null)
    val applyOutcome: StateFlow<MonitorTemplateApplyOutcome?> = mutableApplyOutcome.asStateFlow()

    /** @return the new template's id, so the editor can open it straight away. */
    fun create(name: String): String {
        val id = UUID.randomUUID().toString()
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) {
            viewModelScope.launch {
                repository.updateTemplates { it + MonitorTemplate(id, trimmed, emptyList()) }
            }
        }
        return id
    }

    fun rename(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        update(id) { it.copy(name = trimmed) }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repository.updateTemplates { templates -> templates.filterNot { it.id == id } }
        }
    }

    fun setOperations(id: String, operationNames: Set<String>) {
        update(id) { it.withOperations(operationNames) }
    }

    /** @param seconds null switches the interval off, reporting every access. */
    fun setThrottle(id: String, operationName: String, seconds: Int?) {
        if (seconds != null && !MonitorThrottles.isValid(seconds)) return
        updateRule(id, operationName) { it.copy(throttleSeconds = seconds) }
    }

    fun setHeadsUp(id: String, operationName: String, headsUp: Boolean?) {
        updateRule(id, operationName) { it.copy(headsUp = headsUp) }
    }

    fun setOutcomes(id: String, operationName: String, outcomes: MonitorOutcomes) {
        updateRule(id, operationName) { it.copy(outcomes = outcomes) }
    }

    fun setBackgroundOnly(id: String, operationName: String, backgroundOnly: Boolean) {
        updateRule(id, operationName) { it.copy(backgroundOnly = backgroundOnly) }
    }

    /**
     * The watched apps and operations cannot change while the monitor runs, and
     * a template changes both. The screen already closes this path then, and the
     * setting is read again here so a monitor switched on in the meantime still
     * keeps its configuration.
     */
    fun apply(template: MonitorTemplate, packageNames: Collection<String>) {
        viewModelScope.launch {
            if (settingsRepository.settings.first().backgroundMonitor) {
                mutableApplyOutcome.value = MonitorTemplateApplyOutcome.Locked
                return@launch
            }
            val result = repository.applyTemplate(template, packageNames) { operationName ->
                AppOpCodes.codeOf(operationName) != null
            }
            mutableApplyOutcome.value = MonitorTemplateApplyOutcome.Applied(
                appCount = result.packageNames.size,
                operations = result.appliedOperations,
                skippedOperations = result.skippedOperations,
            )
        }
    }

    fun dismissApplyOutcome() {
        mutableApplyOutcome.value = null
    }

    private fun updateRule(
        id: String,
        operationName: String,
        change: (MonitorTemplateRule) -> MonitorTemplateRule,
    ) {
        update(id) { it.withRule(operationName, change) }
    }

    private fun update(id: String, change: (MonitorTemplate) -> MonitorTemplate) {
        if (BuiltInMonitorTemplates.isBuiltIn(id)) return
        viewModelScope.launch {
            repository.updateTemplates { templates ->
                templates.map { if (it.id == id) change(it) else it }
            }
        }
    }
}
