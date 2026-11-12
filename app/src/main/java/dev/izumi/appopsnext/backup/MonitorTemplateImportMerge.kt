package dev.izumi.appopsnext.backup

import dev.izumi.appopsnext.monitor.BuiltInMonitorTemplates
import dev.izumi.appopsnext.monitor.MonitorTemplate

data class MonitorTemplateMergeResult(
    val templates: List<MonitorTemplate>,
    val added: Int,
    /** Imported templates already present with the same name and rules. */
    val unchanged: Int,
)

/**
 * Adds imported monitor templates to the current ones, the way permission
 * templates are imported, so a restore never loses a template made since the
 * backup was taken.
 *
 * A template that matches a local one in name and rules is not added again, which
 * keeps a repeated import from doubling the list. One whose id is taken by a
 * different local template is added under a new id, so both stay. Built-in
 * templates belong to the app and are never imported.
 */
object MonitorTemplateImportMerge {
    fun merge(
        current: List<MonitorTemplate>,
        imported: List<MonitorTemplate>,
        newId: () -> String,
    ): MonitorTemplateMergeResult {
        val merged = current.toMutableList()
        var added = 0
        var unchanged = 0
        imported.filterNot { BuiltInMonitorTemplates.isBuiltIn(it.id) }.forEach { template ->
            if (merged.any { it.name == template.name && it.rules == template.rules }) {
                unchanged++
                return@forEach
            }
            val takenIds = merged.map(MonitorTemplate::id).toSet()
            var id = template.id
            while (id in takenIds || BuiltInMonitorTemplates.isBuiltIn(id)) id = newId()
            merged += template.copy(id = id)
            added++
        }
        return MonitorTemplateMergeResult(merged, added, unchanged)
    }
}
