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

    @Test
    fun `reads the Android 17 table and pairs the count row with the duration row`() {
        val result = parser.parse("CAMERA", ANDROID_17_TABLE)

        assertEquals(1, result.size)
        val event = result.single()
        assertEquals(10186, event.uid)
        assertEquals("com.android.camera2", event.packageName)
        assertEquals(null, event.attributionTag)
        assertEquals(1075L, event.durationMillis)
        assertEquals("top", event.uidState)
        assertEquals("s", event.flags)
        assertEquals(timestamp("2026-10-28 16:40:34.156"), event.accessTimeMillis)
    }

    private companion object {
        val ANDROID_17_TABLE = """
            AppOps Historical Service State:

            History:  mode=HISTORICAL_MODE_ENABLED_ACTIVE
            Database Total records: SHORT_INTERVAL=10, LONG_INTERVAL=0
            ------------Discrete App Ops-------------

            UID|PACKAGE_NAME|DEVICE_ID|OP_NAME|ATTRIBUTION_TAG|ACCESS_TIME|ACCESS_COUNTS|REJECT_COUNTS|DURATION|UID_STATE|OP_FLAGS|ATTRIBUTION_CHAIN_ID|ATTRIBUTION_FLAGS
            10186|com.android.camera2|default:0|CAMERA|null|2026-10-28 16:40:34.156|0|0|1075|top|s|-1|1
            10186|com.android.camera2|default:0|CAMERA|null|2026-10-28 16:40:34.156|1|0|0|top|s|-1|1
            10186|com.android.camera2|default:0|RECORD_AUDIO|null|2026-10-28 16:40:35.000|1|0|0|top|s|-1|1
            10190|com.example|default:0|CAMERA|tag|2026-10-28 16:41:00.000|0|1|0|fg|s|-1|1
        """.trimIndent()
    }
}
