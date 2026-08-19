package dev.izumi.appopsnext.templates

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpNames
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.templates.model.PermissionTemplate
import dev.izumi.appopsnext.templates.model.PermissionTemplateRule

object NewAppPolicyTemplate {
    const val ID = "builtin:new-app-policy"
    const val INTERNAL_NAME = "New app defaults"

    val defaultTemplate = PermissionTemplate(
        id = ID,
        name = INTERNAL_NAME,
        rules = listOf(
            "FINE_LOCATION",
            "COARSE_LOCATION",
            "READ_CLIPBOARD",
            "CAMERA",
            "RECORD_AUDIO",
            "POST_NOTIFICATION",
            "READ_CONTACTS",
        ).map { operationName ->
            PermissionTemplateRule(
                stableOperationName = AppOpNames.stableName(operationName),
                mode = AppOpMode.IGNORE,
                scope = AppOpScope.PACKAGE,
            )
        },
    )

    fun isBuiltIn(templateId: String): Boolean = templateId == ID

    fun ensurePresent(
        templates: List<PermissionTemplate>,
    ): List<PermissionTemplate> {
        val storedTemplate = templates.firstOrNull { isBuiltIn(it.id) }
        val protectedTemplate = storedTemplate
            ?.copy(name = INTERNAL_NAME)
            ?: defaultTemplate
        return listOf(protectedTemplate) +
            templates.filterNot { isBuiltIn(it.id) }
    }
}
