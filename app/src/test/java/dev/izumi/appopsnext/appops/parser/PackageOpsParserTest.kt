package dev.izumi.appopsnext.appops.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageOpsParserTest {
    private val parser = PackageOpsParser()

    @Test
    fun `parses uid mode and package entries`() {
        val snapshot = parser.parse(
            packageName = "dev.izumi.appopsnext",
            rawOutput = """
                Uid mode: CAMERA: foreground
                RECORD_AUDIO: ignore; rejectTime=+2m15s ago
                android:read_clipboard: allow; time=+5s ago; duration=+12ms
            """.trimIndent(),
        )

        assertEquals(3, snapshot.entries.size)
        assertTrue(snapshot.entries[0].hasUidModePrefix)
        assertEquals("CAMERA", snapshot.entries[0].name)
        assertEquals("foreground", snapshot.entries[0].mode)
        assertFalse(snapshot.entries[1].hasUidModePrefix)
        assertEquals("rejectTime=+2m15s ago", snapshot.entries[1].details)
        assertEquals("android:read_clipboard", snapshot.entries[2].name)
    }

    @Test
    fun `keeps MIUI operations under the number cmd appops accepts`() {
        val snapshot = parser.parse(
            packageName = "com.tencent.mm",
            rawOutput = """
                MIUIOP(10001): ask
                MIUIOP(10008): allow; time=+11m4s ago; rejectTime=+44d19h ago
            """.trimIndent(),
        )

        assertEquals(listOf("10001", "10008"), snapshot.entries.map { it.name })
        assertEquals(listOf("ask", "allow"), snapshot.entries.map { it.mode })
        assertEquals("time=+11m4s ago; rejectTime=+44d19h ago", snapshot.entries[1].details)
    }

    @Test
    fun `returns an empty list for no operations`() {
        val snapshot = parser.parse(
            packageName = "dev.izumi.appopsnext",
            rawOutput = "No operations.\n",
        )

        assertTrue(snapshot.entries.isEmpty())
    }

    @Test
    fun `ignores unrelated output`() {
        assertNull(parser.parseLine("Default mode: allow"))
        assertNull(parser.parseLine("not an app-op line"))
    }
}
