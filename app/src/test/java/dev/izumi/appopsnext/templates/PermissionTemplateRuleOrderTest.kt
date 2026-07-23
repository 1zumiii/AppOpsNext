package dev.izumi.appopsnext.templates

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.templates.model.PermissionTemplateRule
import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionTemplateRuleOrderTest {
    @Test
    fun `explicit order moves known rules and retains unspecified rules`() {
        val camera = rule("android:camera")
        val microphone = rule("android:record_audio")
        val location = rule("android:fine_location")

        assertEquals(
            listOf(location, camera, microphone),
            PermissionTemplateRuleOrder.apply(
                currentRules = listOf(camera, microphone, location),
                orderedOperationNames = listOf(
                    "FINE_LOCATION",
                    "android:camera",
                ),
            ),
        )
    }

    private fun rule(operationName: String) = PermissionTemplateRule(
        stableOperationName = operationName,
        mode = AppOpMode.DEFAULT,
        scope = AppOpScope.UID,
    )
}
