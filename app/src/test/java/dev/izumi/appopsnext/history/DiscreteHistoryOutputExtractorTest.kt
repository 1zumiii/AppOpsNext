package dev.izumi.appopsnext.history

import org.junit.Assert.assertEquals
import org.junit.Test

class DiscreteHistoryOutputExtractorTest {
    @Test
    fun `removes aggregate state before discrete history`() {
        val output = """
            Current AppOps Service state:
              Op CAMERA:
                Access: 123
            Historical AppOps Service state:
              Overly large aggregate section
            Discrete accesses:
              Uid: 10123
                Package: com.example
        """.trimIndent()

        assertEquals(
            """
                Discrete accesses:
                  Uid: 10123
                    Package: com.example
            """.trimIndent(),
            DiscreteHistoryOutputExtractor.extract(output),
        )
    }

    @Test
    fun `returns empty output when discrete history is unavailable`() {
        assertEquals(
            "",
            DiscreteHistoryOutputExtractor.extract(
                "Current AppOps Service state:",
            ),
        )
    }
}
