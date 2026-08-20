package dev.izumi.appopsnext.templates

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.templates.model.PermissionTemplateRule
import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionTemplateRuleSelectionTest {
    @Test
    fun `selection preserves existing settings and appends new defaults`() {
        val camera = PermissionTemplateRule(
            stableOperationName = "android:camera",
            mode = AppOpMode.IGNORE,
            scope = AppOpScope.UID,
        )
        val microphone = PermissionTemplateRule(
            stableOperationName = "android:record_audio",
            mode = AppOpMode.ALLOW,
            scope = AppOpScope.PACKAGE,
        )

        assertEquals(
            listOf(
                microphone,
                PermissionTemplateRule(
                    stableOperationName = "android:fine_location",
                    mode = AppOpMode.DEFAULT,
                    scope = AppOpScope.PACKAGE,
                ),
            ),
            PermissionTemplateRuleSelection.apply(
                currentRules = listOf(camera, microphone),
                selectedOperationNames = listOf(
                    "RECORD_AUDIO",
                    "android:fine_location",
                    "FINE_LOCATION",
                ),
            ),
        )
    }
}
