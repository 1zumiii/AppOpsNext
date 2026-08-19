package dev.izumi.appopsnext.templates

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.templates.model.PermissionTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewAppPolicyTemplateTest {
    @Test
    fun `built in template is inserted first with privacy defaults`() {
        val custom = PermissionTemplate("custom", "Custom", emptyList())

        val templates = NewAppPolicyTemplate.ensurePresent(listOf(custom))

        assertEquals(NewAppPolicyTemplate.ID, templates.first().id)
        assertEquals(custom, templates.last())
        assertTrue(
            templates.first().rules.all {
                it.mode == AppOpMode.IGNORE &&
                    it.scope == AppOpScope.PACKAGE
            },
        )
    }

    @Test
    fun `stored rule edits survive while protected name is normalized`() {
        val edited = NewAppPolicyTemplate.defaultTemplate.copy(
            name = "localized stale name",
            rules = NewAppPolicyTemplate.defaultTemplate.rules.take(1),
        )

        val templates = NewAppPolicyTemplate.ensurePresent(listOf(edited))

        assertEquals(NewAppPolicyTemplate.INTERNAL_NAME, templates.single().name)
        assertEquals(edited.rules, templates.single().rules)
    }
}
