package dev.izumi.appopsnext.history

import org.junit.Assert.assertEquals
import org.junit.Test

class AppOpsHistoryOutputExtractorTest {
    @Test
    fun `keeps matching aggregate entries and the discrete section`() {
        val output = """
            Current AppOps Service state:
              History:
                snapshot:
                  begin = 2026-07-29 09:29:47.516  (-25m)
                  end = 2026-07-29 09:44:47.516  (-10m)
                  Uid u0a166:
                    Package: malformed-noise
                    Package com.example.keyboard:
                      Attribution null:
                        READ_CLIPBOARD:
                          [bg-s] = access=3
                    Package com.example.empty:
                      Attribution null:
            Discrete accesses:
              Uid: 10123
                Package: com.example
        """.trimIndent()

        assertEquals(
            """
                Aggregated accesses:
                  snapshot:
                    begin = 2026-07-29 09:29:47.516  (-25m)
                    end = 2026-07-29 09:44:47.516  (-10m)
                    Uid u0a166:
                      Package com.example.keyboard:
                        Attribution null:
                          READ_CLIPBOARD:
                            [bg-s] = access=3
                Discrete accesses:
                  Uid: 10123
                    Package: com.example
            """.trimIndent(),
            AppOpsHistoryOutputExtractor.extract(
                commandOutput = output,
                operationName = "READ_CLIPBOARD",
            ),
        )
    }

    @Test
    fun `returns empty output when no history is available`() {
        assertEquals(
            "",
            AppOpsHistoryOutputExtractor.extract(
                commandOutput = "Current AppOps Service state:",
                operationName = "READ_CLIPBOARD",
            ),
        )
    }

    @Test
    fun `keeps the Android 17 table header and only the requested rows`() {
        val output = """
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

        val extracted = AppOpsHistoryOutputExtractor.extract(output, "CAMERA")

        assertEquals(
            listOf(
                "UID|PACKAGE_NAME|DEVICE_ID|OP_NAME|ATTRIBUTION_TAG|ACCESS_TIME|ACCESS_COUNTS|REJECT_COUNTS|" +
                    "DURATION|UID_STATE|OP_FLAGS|ATTRIBUTION_CHAIN_ID|ATTRIBUTION_FLAGS",
                "10186|com.android.camera2|default:0|CAMERA|null|2026-10-28 16:40:34.156|0|0|1075|top|s|-1|1",
                "10186|com.android.camera2|default:0|CAMERA|null|2026-10-28 16:40:34.156|1|0|0|top|s|-1|1",
                "10190|com.example|default:0|CAMERA|tag|2026-10-28 16:41:00.000|0|1|0|fg|s|-1|1",
            ),
            extracted.lines().filter(String::isNotEmpty),
        )
    }
}
