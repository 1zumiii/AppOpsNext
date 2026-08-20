package dev.izumi.appopsnext.templates

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpNames
import dev.izumi.appopsnext.templates.model.PermissionTemplateRule

object PermissionTemplateRuleSelection {
    fun apply(
        currentRules: List<PermissionTemplateRule>,
        selectedOperationNames: List<String>,
    ): List<PermissionTemplateRule> {
        val currentByName = currentRules.associateBy {
            AppOpNames.stableName(it.stableOperationName)
        }
        return selectedOperationNames
            .map(AppOpNames::stableName)
            .distinct()
            .map { operationName ->
                currentByName[operationName] ?: PermissionTemplateRule(
                    stableOperationName = operationName,
                    mode = AppOpMode.DEFAULT,
                    scope = PermissionTemplateDefaults.suggestedScope(),
                )
            }
    }
}
