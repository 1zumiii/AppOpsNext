package dev.izumi.appopsnext.backup

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.templates.NewAppPolicyTemplate
import dev.izumi.appopsnext.templates.model.PermissionTemplate
import dev.izumi.appopsnext.templates.model.PermissionTemplateRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateImportMergeTest {
    private val camera = PermissionTemplateRule("android:camera", AppOpMode.IGNORE, AppOpScope.PACKAGE)
    private val audio = PermissionTemplateRule("android:record_audio", AppOpMode.IGNORE, AppOpScope.UID)
    private val local = PermissionTemplate("shared-id", "Local", listOf(camera))
    private var counter = 0
    private val newId = { "new-${++counter}" }

    @Test fun `templates with free IDs are added as they are`() {
        val imported = PermissionTemplate("other", "Imported", listOf(audio))
        val result = TemplateImportMerge.merge(listOf(local), listOf(imported), newId)
        assertEquals(listOf(NewAppPolicyTemplate.ID, "shared-id", "other"), result.templates.map { it.id })
        assertEquals(1, result.added)
    }

    @Test fun `an ID conflict keeps both under a new ID`() {
        val imported = PermissionTemplate("shared-id", "Imported", listOf(audio))
        val result = TemplateImportMerge.merge(listOf(local), listOf(imported), newId)
        assertEquals(local, result.templates[1])
        assertEquals(imported.copy(id = "new-1"), result.templates[2])
        assertEquals(1, result.added)
    }

    @Test fun `an identical template is not added twice`() {
        val first = TemplateImportMerge.merge(listOf(local), listOf(local.copy(id = "elsewhere")), newId)
        assertEquals(2, first.templates.size)
        assertEquals(0, first.added)
        assertEquals(1, first.unchanged)
    }

    @Test fun `a new ID never reuses a taken one`() {
        val taken = PermissionTemplate("new-1", "Taken", listOf(audio))
        val imported = PermissionTemplate("shared-id", "Imported", listOf(audio, camera))
        val result = TemplateImportMerge.merge(listOf(local, taken), listOf(imported), newId)
        assertEquals("new-2", result.templates.last().id)
    }

    @Test fun `the new-app defaults keep their ID and name and take imported rules`() {
        val imported = NewAppPolicyTemplate.defaultTemplate.copy(name = "Renamed", rules = listOf(audio))
        val result = TemplateImportMerge.merge(listOf(local), listOf(imported), newId)
        val builtIn = result.templates.first()
        assertEquals(NewAppPolicyTemplate.ID, builtIn.id)
        assertEquals(NewAppPolicyTemplate.INTERNAL_NAME, builtIn.name)
        assertEquals(listOf(audio), builtIn.rules)
        assertTrue(result.builtInUpdated)
        assertEquals(1, result.templates.count { NewAppPolicyTemplate.isBuiltIn(it.id) })
    }

    @Test fun `the new-app defaults stay when the backup has none`() {
        val result = TemplateImportMerge.merge(emptyList(), listOf(local), newId)
        assertEquals(NewAppPolicyTemplate.defaultTemplate, result.templates.first())
        assertFalse(result.builtInUpdated)
    }
}
