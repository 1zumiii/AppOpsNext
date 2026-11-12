package dev.izumi.appopsnext.backup

import dev.izumi.appopsnext.monitor.BuiltInMonitorTemplates
import dev.izumi.appopsnext.monitor.MonitorTemplate
import dev.izumi.appopsnext.monitor.MonitorTemplateRule
import org.junit.Assert.assertEquals
import org.junit.Test

class MonitorTemplateImportMergeTest {
    private val camera = listOf(MonitorTemplateRule("android:camera", headsUp = true))
    private val sms = listOf(MonitorTemplateRule("android:read_sms"))

    @Test
    fun `imported templates are added after the current ones`() {
        val current = listOf(MonitorTemplate("a", "Local", camera))
        val imported = listOf(MonitorTemplate("b", "Imported", sms))
        val result = MonitorTemplateImportMerge.merge(current, imported) { "new" }
        assertEquals(current + imported, result.templates)
        assertEquals(1, result.added)
        assertEquals(0, result.unchanged)
    }

    @Test
    fun `a template already present is not added again`() {
        val current = listOf(MonitorTemplate("a", "Same", camera))
        val result = MonitorTemplateImportMerge.merge(current, listOf(MonitorTemplate("z", "Same", camera))) { "new" }
        assertEquals(current, result.templates)
        assertEquals(0, result.added)
        assertEquals(1, result.unchanged)
    }

    @Test
    fun `a different template with a taken id gets a new id`() {
        val current = listOf(MonitorTemplate("a", "Local", camera))
        val ids = ArrayDeque(listOf("a", "fresh"))
        val result = MonitorTemplateImportMerge.merge(current, listOf(MonitorTemplate("a", "Other", sms))) {
            ids.removeFirst()
        }
        assertEquals(listOf("a", "fresh"), result.templates.map(MonitorTemplate::id))
        assertEquals(1, result.added)
    }

    @Test
    fun `built-in templates are never imported`() {
        val builtIn = BuiltInMonitorTemplates.all.first()
        val result = MonitorTemplateImportMerge.merge(emptyList(), listOf(builtIn)) { "new" }
        assertEquals(emptyList<MonitorTemplate>(), result.templates)
        assertEquals(0, result.added)
    }
}
