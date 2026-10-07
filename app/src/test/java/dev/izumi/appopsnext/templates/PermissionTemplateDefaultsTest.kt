package dev.izumi.appopsnext.templates

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionTemplateDefaultsTest {
    @Test
    fun `common rules use stable names neutral modes and package scope`() {
        assertTrue(PermissionTemplateDefaults.commonRules.isNotEmpty())
        assertTrue(
            PermissionTemplateDefaults.commonRules.all {
                it.stableOperationName.startsWith("android:") &&
                    it.mode == AppOpMode.DEFAULT
            },
        )
        assertEquals(
            AppOpScope.PACKAGE,
            PermissionTemplateDefaults.suggestedScope(),
        )
        assertTrue(
            PermissionTemplateDefaults.commonRules.all {
                it.scope == AppOpScope.PACKAGE
            },
        )
    }
}
