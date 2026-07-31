package dev.izumi.appopsnext.history.parser

import dev.izumi.appopsnext.history.model.AppOpHistoryEvent
import java.text.SimpleDateFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiscreteAppOpsHistoryParserTest {
    private val parser = DiscreteAppOpsHistoryParser()

    @Test
    fun `parses only the discrete section and sorts newest first`() {
        val result = parser.parse(
            operationName = "CAMERA",
            output = """
                Current AppOps Service state:
                  Package com.example.noise:
                    CAMERA: allow
                Discrete accesses:
                  Largest chain id: 0
                  Uid: 10123
                    Package: com.example.camera
                      CAMERA
                        Attribution: null
                          Access [top-s] at 2026-07-23 18:26:00.000
                          Access [fg-s] at 2026-07-24 09:10:00.000 for 60000 milliseconds
                  Uid: 10456
                    Package: com.example.scanner
                      CAMERA
                        Device: default:0
                          Attribution: barcode
                            Access [bg-p] at 2026-07-22 12:14:00.000 for 120000 milliseconds
            """.trimIndent(),
        )

        assertEquals(3, result.size)
        assertEquals("com.example.camera", result[0].packageName)
        assertEquals("fg", result[0].uidState)
        assertEquals("s", result[0].flags)
        assertEquals(60_000L, result[0].durationMillis)
        assertNull(result[1].attributionTag)
        assertEquals("barcode", result[2].attributionTag)
        assertEquals(120_000L, result[2].durationMillis)
        assertEquals(
            timestamp("2026-07-22 12:14:00.000"),
            result[2].accessTimeMillis,
        )
    }

    @Test
    fun `returns empty when the system has no discrete section`() {
        assertEquals(
            emptyList<Any>(),
            parser.parse("CAMERA", "Current AppOps Service state:"),
        )
    }

    @Test
    fun `merges a duration-less companion into its duration event`() {
        val result = parser.parse(
            operationName = "CAMERA",
            output = """
                Discrete accesses:
                  Uid: 10123
                    Package: com.example.camera
                      CAMERA
                        Attribution: null
                          Access [top-s] at 2026-07-31 15:04:00.000
                          Access [top-s] at 2026-07-31 15:04:00.000 for 60000 milliseconds
            """.trimIndent(),
        )

        assertEquals(1, result.size)
        assertEquals(60_000L, result.single().durationMillis)
    }

    @Test
    fun `keeps unpaired and differently identified accesses`() {
        val result = parser.parse(
            operationName = "CAMERA",
            output = """
                Discrete accesses:
                  Uid: 10123
                    Package: com.example.camera
                      CAMERA
                        Attribution: null
                          Access [top-s] at 2026-07-31 15:04:00.000
                          Access [top-s] at 2026-07-31 15:04:00.000
                          Access [top-s] at 2026-07-31 15:04:00.000 for 60000 milliseconds
                          Access [fg-s] at 2026-07-31 15:04:00.000 for 60000 milliseconds
            """.trimIndent(),
        )

        assertEquals(3, result.size)
        assertEquals(1, result.count { it.durationMillis == null })
        assertEquals(
            setOf("top", "fg"),
            result
                .filter { it.durationMillis != null }
                .mapTo(mutableSetOf(), AppOpHistoryEvent::uidState),
        )
    }

    private fun timestamp(value: String): Long =
        checkNotNull(
            SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS",
                Locale.US,
            ).parse(value),
        ).time
}
