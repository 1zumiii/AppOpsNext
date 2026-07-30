package dev.izumi.appopsnext.diagnostics

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DiagnosticLogFormatterTest {
    @Test
    fun `formatter keeps one bounded line`() {
        val formatted = DiagnosticLogFormatter.format(
            timestamp = OffsetDateTime.parse("2026-07-30T12:34:56+08:00"),
            level = DiagnosticLogLevel.ERROR,
            source = "UserService\nBinder",
            message = "Connection\nfailed\twith timeout",
        )

        assertEquals(
            "2026-07-30T12:34:56+08:00 [ERROR] " +
                "[UserService Binder] Connection failed with timeout",
            formatted,
        )
        assertFalse(formatted.contains('\n'))
    }
}
