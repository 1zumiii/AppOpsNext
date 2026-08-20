package dev.izumi.appopsnext.presentation.templates

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.izumi.appopsnext.AppOpsNextApplication
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.templates.NewAppPolicyTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TemplatesViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository =
        getApplication<AppOpsNextApplication>().permissionTemplateRepository
    private val diagnosticLog =
        getApplication<AppOpsNextApplication>().diagnosticLogRepository
    private val selectedTemplateId = MutableStateFlow<String?>(null)
    private val settingsRepository =
        getApplication<AppOpsNextApplication>().userSettingsRepository

    val uiState = combine(
        repository.templates,
        selectedTemplateId,
        settingsRepository.settings,
    ) { templates, selectedId, settings ->
        TemplatesUiState(
            templates = templates,
            selectedTemplate = templates.firstOrNull { it.id == selectedId },
            autoApplyNewAppTemplate = settings.autoApplyNewAppTemplate,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = TemplatesUiState(),
    )

    init {
        viewModelScope.launch {
            repository.templates.collect { templates ->
                diagnosticLog.info(
                    source = LOG_SOURCE,
                    message =
                        "Stored templates loaded. count=${templates.size}",
                )
            }
        }
    }

    fun createTemplate(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            selectedTemplateId.value = repository.create(name)
        }
    }

    fun selectTemplate(templateId: String) {
        selectedTemplateId.value = templateId
    }

    fun closeEditor() {
        selectedTemplateId.value = null
    }

    fun deleteTemplate(templateId: String) {
        if (NewAppPolicyTemplate.isBuiltIn(templateId)) {
            return
        }
        if (selectedTemplateId.value == templateId) {
            selectedTemplateId.value = null
        }
        viewModelScope.launch {
            repository.delete(templateId)
        }
    }

    fun setAutoApplyNewAppTemplate(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoApplyNewAppTemplate(enabled)
        }
    }

    fun setRuleMode(stableOperationName: String, mode: AppOpMode) {
        val template = uiState.value.selectedTemplate ?: return
        val rule = template.rules.firstOrNull {
            it.stableOperationName == stableOperationName
        } ?: return
        viewModelScope.launch {
            repository.updateRule(
                templateId = template.id,
                stableOperationName = stableOperationName,
                mode = mode,
                scope = rule.scope,
            )
        }
    }

    fun setRuleSelection(orderedOperationNames: List<String>) {
        val templateId = uiState.value.selectedTemplate?.id ?: return
        viewModelScope.launch {
            repository.setRuleSelection(templateId, orderedOperationNames)
        }
    }

    private companion object {
        const val LOG_SOURCE = "Templates"
    }

    fun setRuleOrder(orderedOperationNames: List<String>) {
        val templateId = uiState.value.selectedTemplate?.id ?: return
        viewModelScope.launch {
            repository.reorderRules(templateId, orderedOperationNames)
        }
    }
}
