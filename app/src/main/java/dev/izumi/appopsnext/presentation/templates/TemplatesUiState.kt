package dev.izumi.appopsnext.presentation.templates

import dev.izumi.appopsnext.templates.model.PermissionTemplate

data class TemplatesUiState(
    val templates: List<PermissionTemplate> = emptyList(),
    val selectedTemplate: PermissionTemplate? = null,
)
