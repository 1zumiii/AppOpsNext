package dev.izumi.appopsnext.appops.parser

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpStatesParserTest {
    @Test
    fun `UID overrides package and covers siblings without package records`() {
        val result = parse(
            """
              Uid u0a214:
                state=cch
                  CAMERA: mode=foreground
                Package test.one:
                  CAMERA (allow):
                Package test.two:
                  CAMERA (ignore):
            """,
        )
        assertEquals("allow", result.packageModes[10214 to "test.one"])
        for (pkg in listOf("test.one", "test.two", "test.unrecorded")) {
            assertEquals(EffectiveOpState(AppOpMode.FOREGROUND, AppOpScope.UID), result.effective(10214, pkg))
        }
    }

    @Test
    fun `only current user including numeric system UIDs is retained`() {
        val output = """
              Uid 1000:
                  CAMERA: mode=allow
              Uid u0a214:
                  CAMERA: mode=ignore
              Uid u10a214:
                  CAMERA: mode=foreground
              Uid 1001000:
                  CAMERA: mode=deny
        """.trimIndent().prependIndent("  ")
        assertEquals(mapOf(1000 to "allow", 10214 to "ignore"), OpStatesParser.parse(output, "CAMERA", 0).uidModes)
        assertEquals(mapOf(1010214 to "foreground", 1001000 to "deny"), OpStatesParser.parse(output, "CAMERA", 10).uidModes)
    }

    @Test
    fun `package modes stay separate and missing records default`() {
        val result = parse(
            """
              Uid u0a214:
                Package test.one:
                  CAMERA (allow):
                    null=[
                      Access: [top-s] 2026-10-07 duration=+1s
                      Reject: [cch-s] 2026-10-07
                    ]
                Package test.two:
                  CAMERA (ignore):
              Uid u0a215:
                Package test.three:
                  RECORD_AUDIO (deny):
            """,
        )
        assertEquals(EffectiveOpState(AppOpMode.ALLOW, AppOpScope.PACKAGE), result.effective(10214, "test.one"))
        assertEquals(EffectiveOpState(AppOpMode.IGNORE, AppOpScope.PACKAGE), result.effective(10214, "test.two"))
        assertEquals(EffectiveOpState(AppOpMode.DEFAULT, AppOpScope.PACKAGE), result.effective(10215, "test.three"))
        assertEquals(EffectiveOpState(AppOpMode.DEFAULT, AppOpScope.PACKAGE), result.effective(10214, "test.absent"))
    }

    @Test
    fun `non UID sections and history cannot create modes`() {
        val result = OpStatesParser.parse(
            """
            Current AppOps Service state:
              Settings:
                  CAMERA: mode=deny
              Op mode watchers:
                Op CAMERA:
                  CAMERA: mode=ignore
              Uid u0a1:
                Package test.one:
                  CAMERA (default):
                    CAMERA: mode=deny
              Historical ops:
                  CAMERA: mode=allow
            """.trimIndent(), "android:camera", 0,
        )
        assertTrue(result.uidModes.isEmpty())
        assertEquals(mapOf((10001 to "test.one") to "default"), result.packageModes)
    }

    @Test
    fun `malformed UID clears previous package context`() {
        val result = parse("""
              Uid u0a1:
                  CAMERA: mode=allow
              Uid u999999999999a9:
                  CAMERA: mode=deny
              Uid invalid:
                Package test.invalid:
                  CAMERA (ignore):
        """)
        assertEquals(mapOf(10001 to "allow"), result.uidModes)
        assertTrue(result.packageModes.isEmpty())
    }

    @Test
    fun `unknown UID mode stays authoritative without failing other rows`() {
        val result = parse("""
              Uid u0a1:
                  CAMERA: mode=vendor-mode
                Package test.one:
                  CAMERA (allow):
              Uid u0a2:
                Package test.two:
                  CAMERA (ignore):
        """)
        val unknown = result.effective(10001, "test.one")
        assertNull(unknown.mode)
        assertEquals("vendor-mode", unknown.rawMode)
        assertEquals(AppOpScope.UID, unknown.scope)
        assertEquals(AppOpMode.IGNORE, result.effective(10002, "test.two").mode)
    }

    @Test
    fun `switch annotation preserves the package record before the switch`() {
        val result = OpStatesParser.parse(
            output = """
              Uid u0a1:
                Package test.one:
                  MONITOR_LOCATION (allow / switch COARSE_LOCATION=foreground):
                    Access: [top-s] 2026-10-07
                Package test.two:
                  MONITOR_LOCATION (ignore / switch COARSE_LOCATION=allow):
              Uid u10a1:
                Package test.otheruser:
                  MONITOR_LOCATION (deny / switch COARSE_LOCATION=ignore):
            """.trimIndent().prependIndent("  "),
            operationName = "MONITOR_LOCATION",
            userId = 0,
        )
        assertEquals(2, result.packageModes.size)
        assertEquals(AppOpMode.ALLOW, result.effective(10001, "test.one").mode)
        assertEquals(AppOpMode.IGNORE, result.effective(10001, "test.two").mode)
    }

    @Test
    fun `unknown package mode is preserved including with switch suffix`() {
        val result = parse("""
              Uid u0a1:
                Package test.one:
                  CAMERA (vendor-mode / switch OTHER=allow):
        """)
        val unknown = result.effective(10001, "test.one")
        assertNull(unknown.mode)
        assertEquals("vendor-mode", unknown.rawMode)
        assertEquals(AppOpScope.PACKAGE, unknown.scope)
    }

    private fun parse(output: String) = OpStatesParser.parse(output.trimIndent().prependIndent("  "), "CAMERA", 0)
}
