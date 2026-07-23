package dev.izumi.appopsnext.templates.model

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpScope

data class PermissionTemplate(
    val id: String,
    val name: String,
    val rules: List<PermissionTemplateRule>,
)

data class PermissionTemplateRule(
    val stableOperationName: String,
    val mode: AppOpMode,
    val scope: AppOpScope,
)
