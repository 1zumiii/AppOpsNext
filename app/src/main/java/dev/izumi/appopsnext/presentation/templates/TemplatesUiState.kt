package dev.izumi.appopsnext.presentation.templates

import dev.izumi.appopsnext.templates.model.PermissionTemplate

data class TemplatesUiState(
    val templates: List<PermissionTemplate> = emptyList(),
    val selectedTemplate: PermissionTemplate? = null,
    val autoApplyNewAppTemplate: Boolean = false,
    val instantNewAppRules: Boolean = true,
    /** The install watcher is still starting or ending, so its switch waits. */
    val instantNewAppRulesSettling: Boolean = false,
    val restoreOnReinstall: Boolean = false,
)
