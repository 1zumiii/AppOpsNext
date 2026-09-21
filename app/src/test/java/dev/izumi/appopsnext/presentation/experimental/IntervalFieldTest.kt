package dev.izumi.appopsnext.presentation.experimental

import org.junit.Assert.assertEquals
import org.junit.Test

class IntervalFieldTest {
    @Test
    fun `a run of leading zeros collapses to one digit`() {
        assertEquals("0", intervalFieldDigits("0000"))
        assertEquals("0", intervalFieldDigits("0"))
        assertEquals("5", intervalFieldDigits("05"))
        assertEquals("30", intervalFieldDigits("0030"))
        assertEquals("50", intervalFieldDigits("50"))
    }

    /** An empty box is how a value is cleared before a new one is typed. */
    @Test
    fun `an empty field stays empty rather than becoming zero`() {
        assertEquals("", intervalFieldDigits(""))
        assertEquals("", intervalFieldDigits("abc"))
    }

    @Test
    fun `anything that is not a digit is refused`() {
        assertEquals("12", intervalFieldDigits("1a2-"))
        assertEquals("3", intervalFieldDigits(" 3 "))
        assertEquals("15", intervalFieldDigits("1.5"))
    }

    @Test
    fun `a field cannot grow past what an interval can mean`() {
        assertEquals("1234", intervalFieldDigits("123456"))
    }

    @Test
    fun `a part left empty counts as zero but a missing number does not`() {
        assertEquals(90, intervalFieldSeconds("1", "30"))
        assertEquals(30, intervalFieldSeconds("", "30"))
        assertEquals(60, intervalFieldSeconds("1", ""))
        assertEquals(0, intervalFieldSeconds("", ""))
    }
}
