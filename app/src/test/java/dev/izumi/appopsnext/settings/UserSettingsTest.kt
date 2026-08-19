package dev.izumi.appopsnext.settings

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class UserSettingsTest {
    @Test
    fun `system applications are hidden by default`() {
        assertTrue(UserSettings().hideSystemApps)
    }

    @Test
    fun `deny fallback success notice is shown by default`() {
        assertFalse(UserSettings().suppressDenyFallbackNotice)
    }

    @Test
    fun `automatic new app policy is opt in`() {
        assertFalse(UserSettings().autoApplyNewAppTemplate)
    }
}
