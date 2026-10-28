package dev.izumi.appopsnext.backup

import dev.izumi.appopsnext.templates.NewAppPolicyTemplate
import dev.izumi.appopsnext.templates.model.PermissionTemplate

data class TemplateMergeResult(
    val templates: List<PermissionTemplate>,
    val added: Int,
    /** Imported templates already present with the same name and rules. */
    val unchanged: Int,
    /** Whether the new-app defaults took the imported rules. */
    val builtInUpdated: Boolean,
)

/**
 * Adds imported templates to the current ones without losing either side.
 *
 * A template whose ID is taken by a different local template is added under a new
 * ID, so both stay. One that matches a local template in name and rules is not
 * added again, which keeps a repeated import from doubling the list. The new-app
 * defaults stay one protected template: an import can change their rules but
 * never their ID or name.
 */
object TemplateImportMerge {
    fun merge(
        current: List<PermissionTemplate>,
        imported: List<PermissionTemplate>,
        newId: () -> String,
    ): TemplateMergeResult {
        val currentTemplates = NewAppPolicyTemplate.ensurePresent(current)
        val currentBuiltIn = currentTemplates.first()
        val importedBuiltIn = imported.firstOrNull { NewAppPolicyTemplate.isBuiltIn(it.id) }
        val importedRules = importedBuiltIn?.rules?.takeIf { it != currentBuiltIn.rules }
        val builtInUpdated = importedRules != null
        val builtIn = importedRules?.let { currentBuiltIn.copy(rules = it) } ?: currentBuiltIn

        val merged = currentTemplates.drop(1).toMutableList()
        var added = 0
        var unchanged = if (importedBuiltIn != null && !builtInUpdated) 1 else 0
        imported.filterNot { NewAppPolicyTemplate.isBuiltIn(it.id) }.forEach { template ->
            if (merged.any { it.name == template.name && it.rules == template.rules }) {
                unchanged++
                return@forEach
            }
            val takenIds = merged.map(PermissionTemplate::id) + NewAppPolicyTemplate.ID
            var id = template.id
            while (id in takenIds) id = newId()
            merged += template.copy(id = id)
            added++
        }
        return TemplateMergeResult(
            templates = listOf(builtIn) + merged,
            added = added,
            unchanged = unchanged,
            builtInUpdated = builtInUpdated,
        )
    }
}
