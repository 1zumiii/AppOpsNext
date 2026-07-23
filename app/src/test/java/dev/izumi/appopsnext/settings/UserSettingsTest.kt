package dev.izumi.appopsnext.settings

import org.junit.Assert.assertTrue
import org.junit.Test

class UserSettingsTest {
    @Test
    fun `system applications are hidden by default`() {
        assertTrue(UserSettings().hideSystemApps)
    }
}
