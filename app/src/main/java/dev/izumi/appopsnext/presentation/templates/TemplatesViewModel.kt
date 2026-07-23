package dev.izumi.appopsnext.presentation.templates

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.izumi.appopsnext.AppOpsNextApplication
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.templates.PermissionTemplateDefaults
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
    private val selectedTemplateId = MutableStateFlow<String?>(null)

    val uiState = combine(
        repository.templates,
        selectedTemplateId,
    ) { templates, selectedId ->
        TemplatesUiState(
            templates = templates,
            selectedTemplate = templates.firstOrNull { it.id == selectedId },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = TemplatesUiState(),
    )

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
        if (selectedTemplateId.value == templateId) {
            selectedTemplateId.value = null
        }
        viewModelScope.launch {
            repository.delete(templateId)
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

    fun setRuleScope(stableOperationName: String, scope: AppOpScope) {
        val template = uiState.value.selectedTemplate ?: return
        val rule = template.rules.firstOrNull {
            it.stableOperationName == stableOperationName
        } ?: return
        viewModelScope.launch {
            repository.updateRule(
                templateId = template.id,
                stableOperationName = stableOperationName,
                mode = rule.mode,
                scope = scope,
            )
        }
    }

    fun addRule(stableOperationName: String) {
        val template = uiState.value.selectedTemplate ?: return
        viewModelScope.launch {
            repository.updateRule(
                templateId = template.id,
                stableOperationName = stableOperationName,
                mode = AppOpMode.DEFAULT,
                scope = PermissionTemplateDefaults.suggestedScope(
                    stableOperationName,
                ),
            )
        }
    }

    fun removeRule(stableOperationName: String) {
        val templateId = uiState.value.selectedTemplate?.id ?: return
        viewModelScope.launch {
            repository.removeRule(templateId, stableOperationName)
        }
    }
}
