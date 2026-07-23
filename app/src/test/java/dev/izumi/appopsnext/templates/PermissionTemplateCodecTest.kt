package dev.izumi.appopsnext.templates

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.templates.model.PermissionTemplate
import dev.izumi.appopsnext.templates.model.PermissionTemplateRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionTemplateCodecTest {
    @Test
    fun `round trip preserves names rules modes and scopes`() {
        val templates = listOf(
            PermissionTemplate(
                id = "privacy-template",
                name = "隐私 / Privacy\t模板",
                rules = listOf(
                    PermissionTemplateRule(
                        stableOperationName = "android:camera",
                        mode = AppOpMode.IGNORE,
                        scope = AppOpScope.UID,
                    ),
                    PermissionTemplateRule(
                        stableOperationName = "android:run_in_background",
                        mode = AppOpMode.DEFAULT,
                        scope = AppOpScope.PACKAGE,
                    ),
                ),
            ),
        )

        assertEquals(
            templates,
            PermissionTemplateCodec.decode(
                PermissionTemplateCodec.encode(templates),
            ),
        )
    }

    @Test
    fun `invalid or future data fails closed`() {
        assertTrue(PermissionTemplateCodec.decode(null).isEmpty())
        assertTrue(PermissionTemplateCodec.decode("future-format").isEmpty())
    }
}
