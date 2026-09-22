package dev.izumi.appopsnext.history.parser

import java.text.SimpleDateFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AggregatedAppOpsHistoryParserTest {
    private val parser = AggregatedAppOpsHistoryParser()

    @Test fun `reject only and mixed buckets retain counts and interval without inventing accesses`() {
        val result = parser.parse("CAMERA", """
            Aggregated accesses:
              snapshot:
                begin = 2026-09-22 09:00:00.000
                end = 2026-09-22 10:00:00.000
                Uid u0a166:
                  Package com.example.camera:
                    Attribution null:
                      CAMERA:
                        [fgsvc-s] = reject=4
                        [top-s] = access=2, reject=3, duration=+637ms
                        [bg-s] = reject=0
        """.trimIndent())
        assertEquals(2, result.size)
        assertEquals(0, result[0].accessCount)
        assertEquals(4, result[0].rejectCount)
        assertEquals(null, result[0].durationMillis)
        assertEquals(2, result[1].accessCount)
        assertEquals(3, result[1].rejectCount)
        assertEquals(637L, result[1].durationMillis)
        assertTrue(result.all { it.isAggregated })
        assertEquals(timestamp("2026-09-22 09:00:00.000"), result[0].intervalStartTimeMillis)
        assertEquals(timestamp("2026-09-22 10:00:00.000"), result[0].accessTimeMillis)
    }

    @Test
    fun `parses access counts from time bucketed history`() {
        val result = parser.parse(
            operationName = "READ_CLIPBOARD",
            output = """
                Aggregated accesses:
                  snapshot:
                    begin = 2026-07-29 09:29:47.516  (-25m)
                    end = 2026-07-29 09:44:47.516  (-10m)
                    Uid u0a166:
                      Package com.example.keyboard:
                        Attribution null:
                          READ_CLIPBOARD:
                            [bg-s] = access=3
                  snapshot:
                    begin = 2026-07-29 09:51:08.546  (-4m)
                    end = 1970-01-01 08:04:19.523  (-20663d)
                    Uid u10a277:
                      Package com.example.profile:
                        Attribution feature:
                          READ_CLIPBOARD:
                            [top-s] = access=2, duration=+1m3s8ms
                Discrete accesses:
                  Largest chain id: 0
            """.trimIndent(),
        )

        assertEquals(2, result.size)
        assertEquals("com.example.profile", result[0].packageName)
        assertEquals(1_010_277, result[0].uid)
        assertEquals("feature", result[0].attributionTag)
        assertEquals(2, result[0].accessCount)
        assertEquals(63_008L, result[0].durationMillis)
        assertEquals(
            timestamp("2026-07-29 09:51:08.546"),
            result[0].accessTimeMillis,
        )
        assertEquals(10_166, result[1].uid)
        assertEquals(3, result[1].accessCount)
        assertTrue(result.all { it.isAggregated })
    }

    @Test
    fun `uses duration as one active access when count is absent`() {
        val result = parser.parse(
            operationName = "CAMERA",
            output = """
                Aggregated accesses:
                  snapshot:
                    begin = 2026-07-29 09:29:47.516
                    end = 2026-07-29 09:44:47.516
                    Uid 10074:
                      Package com.example.camera:
                        Attribution null:
                          CAMERA:
                            [top-s] = duration=+637ms
            """.trimIndent(),
        )

        assertEquals(1, result.single().accessCount)
        assertEquals(637L, result.single().durationMillis)
    }

    private fun timestamp(value: String): Long =
        checkNotNull(
            SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS",
                Locale.US,
            ).parse(value),
        ).time
}
