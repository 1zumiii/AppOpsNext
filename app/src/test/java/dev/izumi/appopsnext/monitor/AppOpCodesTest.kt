package dev.izumi.appopsnext.monitor

import org.junit.Assert.assertEquals
import org.junit.Test

class AppOpCodesTest {
    @Test fun `every monitored operation resolves to a code`() {
        val missing = AppOpCodes.MONITORED_NAMES.filter { AppOpCodes.codeOf(it) == null }
        assertEquals(emptyList<String>(), missing)
        assertEquals(
            AppOpCodes.MONITORED_NAMES.size,
            AppOpCodes.MONITORED_CODES.size,
        )
    }

    @Test fun `operation codes and names agree in both directions`() {
        AppOpCodes.MONITORED_NAMES.forEach { name ->
            val code = AppOpCodes.codeOf(name)
            assertEquals(name, code?.let(AppOpCodes::nameOf))
        }
        assertEquals(29, AppOpCodes.codeOf("android:read_clipboard"))
    }

    /** Values read from an Android 15 framework's AppOpsManager. */
    @Test fun `codes after the unnamed deprecated operation keep their platform values`() {
        assertEquals(null, AppOpCodes.nameOf(96))
        assertEquals(97, AppOpCodes.codeOf("android:auto_revoke_permissions_if_unused"))
        assertEquals(107, AppOpCodes.codeOf("android:schedule_exact_alarm"))
        assertEquals(131, AppOpCodes.codeOf("android:capture_consentless_bugreport_on_userdebug_build"))
        assertEquals(148, AppOpCodes.codeOf("android:receive_sensitive_notifications"))
    }

    @Test fun `registry names use the platform's internal spelling`() {
        assertEquals("CAMERA", AppOpCodes.registryNameOf(26))
        assertEquals("MONITOR_HIGH_POWER_LOCATION", AppOpCodes.registryNameOf(42))
        assertEquals("RECEIVE_SOUNDTRIGGER_AUDIO", AppOpCodes.registryNameOf(120))
        assertEquals("UNARCHIVAL_CONFIRMATION", AppOpCodes.registryNameOf(146))
    }
}
