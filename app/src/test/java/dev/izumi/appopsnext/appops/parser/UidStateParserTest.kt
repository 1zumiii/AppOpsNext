package dev.izumi.appopsnext.appops.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UidStateParserTest {
    /** Trimmed from `dumpsys appops --package`, which lists one block per user. */
    private val dump = """
        Uid mode default:
          MANAGE_EXTERNAL_STORAGE: ignore
          Uid u0a601:
            state=top
            capability=LCMNFUA
            Package dev.izumi.appopsprobe:
              COARSE_LOCATION (allow):
                Access: top=+1h2m3s
          Uid u10a601:
            state=cch
            capability=-------
    """.trimIndent()

    @Test
    fun `each user profile keeps its own state`() {
        val states = UidStateParser.parse(dump)
        assertEquals("top", states[10601])
        assertEquals("cch", states[1010601])
        assertEquals(2, states.size)
    }

    @Test
    fun `a system uid is printed as a plain number`() {
        val states = UidStateParser.parse(
            """
            Uid 1000:
              state=pers
            Uid 0:
              state=cch
            """.trimIndent(),
        )
        assertEquals("pers", states[1000])
        assertEquals("cch", states[0])
    }

    /**
     * Only the first state after a heading belongs to that UID; a later one is
     * part of some other block and must not overwrite it.
     */
    @Test
    fun `a later state does not overwrite the one that follows the heading`() {
        val states = UidStateParser.parse(
            """
            Uid u0a12:
              state=top
              something state=bg
            """.trimIndent(),
        )
        assertEquals("top", states[10012])
    }

    @Test
    fun `a uid form that cannot be named is skipped rather than guessed at`() {
        val states = UidStateParser.parse(
            """
            Uid u0i5:
              state=top
            Uid u0a7:
              state=bg
            """.trimIndent(),
        )
        assertEquals("bg", states[10007])
        assertEquals(1, states.size)
    }

    @Test
    fun `output with nothing to read yields nothing`() {
        assertTrue(UidStateParser.parse("").isEmpty())
        assertTrue(UidStateParser.parse("No operations.").isEmpty())
        assertNull(UidStateParser.parse("state=top").keys.firstOrNull())
    }

    @Test
    fun `only top means the application is on screen`() {
        assertEquals("top", UidStateParser.STATE_TOP)
        val states = UidStateParser.parse(dump)
        assertTrue(states[10601] == UidStateParser.STATE_TOP)
        assertTrue(states[1010601] != UidStateParser.STATE_TOP)
    }
}
