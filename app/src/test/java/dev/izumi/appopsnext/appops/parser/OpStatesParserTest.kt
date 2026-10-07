package dev.izumi.appopsnext.appops.parser

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpScope
import org.junit.Assert.*
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
        assertEquals(AppOpMode.ALLOW, result.packageModes[10214 to "test.one"])
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
        assertEquals(mapOf(1000 to AppOpMode.ALLOW, 10214 to AppOpMode.IGNORE), OpStatesParser.parse(output, "CAMERA", 0).uidModes)
        assertEquals(mapOf(1010214 to AppOpMode.FOREGROUND, 1001000 to AppOpMode.DENY), OpStatesParser.parse(output, "CAMERA", 10).uidModes)
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
        assertEquals(mapOf((10001 to "test.one") to AppOpMode.DEFAULT), result.packageModes)
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
        assertEquals(mapOf(10001 to AppOpMode.ALLOW), result.uidModes)
        assertTrue(result.packageModes.isEmpty())
    }

    @Test
    fun `unknown mode is an error instead of a misleading default`() {
        assertThrows(IllegalArgumentException::class.java) {
            parse("""
              Uid u0a1:
                  CAMERA: mode=unknown
            """)
        }
    }

    private fun parse(output: String) = OpStatesParser.parse(output.trimIndent().prependIndent("  "), "CAMERA", 0)
}
