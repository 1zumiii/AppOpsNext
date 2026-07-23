package dev.izumi.appopsnext.templates

import dev.izumi.appopsnext.appops.model.AppOpNames
import dev.izumi.appopsnext.templates.model.PermissionTemplateRule

object PermissionTemplateRuleOrder {
    fun apply(
        currentRules: List<PermissionTemplateRule>,
        orderedOperationNames: List<String>,
    ): List<PermissionTemplateRule> {
        val rulesByName =
            currentRules.associateBy(PermissionTemplateRule::stableOperationName)
        val normalizedOrder = orderedOperationNames
            .map(AppOpNames::stableName)
            .distinct()
        return normalizedOrder.mapNotNull(rulesByName::get) +
            currentRules.filterNot {
                it.stableOperationName in normalizedOrder
            }
    }
}
