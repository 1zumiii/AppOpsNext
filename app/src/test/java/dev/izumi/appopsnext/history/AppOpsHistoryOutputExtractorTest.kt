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
}
