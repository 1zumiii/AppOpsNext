package dev.izumi.appopsnext.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserSettingsTest {
    @Test
    fun `defaults hide system apps show fallback notices and keep automatic policy opt in`() {
        val settings = UserSettings()

        assertTrue(settings.hideSystemApps)
        assertFalse(settings.suppressDenyFallbackNotice)
        assertFalse(settings.suppressPermissionFlagNotice)
        assertFalse(settings.autoApplyNewAppTemplate)
    }
}
