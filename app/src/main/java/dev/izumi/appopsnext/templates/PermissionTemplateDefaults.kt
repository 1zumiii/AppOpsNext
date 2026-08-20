package dev.izumi.appopsnext.templates

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpNames
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.templates.model.PermissionTemplateRule

object PermissionTemplateDefaults {
    private val commonOperationNames = listOf(
        "CAMERA",
        "RECORD_AUDIO",
        "COARSE_LOCATION",
        "FINE_LOCATION",
        "READ_CLIPBOARD",
        "POST_NOTIFICATION",
        "READ_CONTACTS",
    )

    val commonRules: List<PermissionTemplateRule> =
        commonOperationNames.map { operationName ->
            PermissionTemplateRule(
                stableOperationName = AppOpNames.stableName(operationName),
                mode = AppOpMode.DEFAULT,
                scope = AppOpScope.PACKAGE,
            )
        }

    fun suggestedScope(): AppOpScope = AppOpScope.PACKAGE
}
