package dev.izumi.appopsnext.appops.model

import dev.izumi.appopsnext.appops.command.AppOpMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiuiOpsTest {
    @Test
    fun `reads the number out of the printed name`() {
        assertEquals("10008", MiuiOps.fromPrinted("MIUIOP(10008)"))
        assertNull(MiuiOps.fromPrinted("CAMERA"))
        assertNull(MiuiOps.fromPrinted("MIUIOP(26)"))
    }

    @Test
    fun `only numbers in the MIUI range are MIUI operations`() {
        assertTrue(MiuiOps.isMiuiOp("10033"))
        assertTrue(MiuiOps.isMiuiOp("10036"))
        assertFalse(MiuiOps.isMiuiOp("26"))
        assertFalse(MiuiOps.isMiuiOp("CAMERA"))
        assertEquals("10021", MiuiOps.nameOf(10021))
        assertNull(MiuiOps.nameOf(26))
    }

    @Test
    fun `ask is offered where the security center prompts`() {
        listOf("10022", "10044", "10051", "10052").forEach {
            assertTrue(AppOpMode.ASK in AppOpMode.choicesFor(it))
        }
        assertFalse(AppOpMode.ASK in AppOpMode.choicesFor("10033"))
        assertFalse(AppOpMode.ASK in AppOpMode.choicesFor("CAMERA"))
        assertFalse(AppOpMode.ASK in AppOpMode.standard)
    }

    @Test
    fun `ask stays on offer where it is the current mode`() {
        assertTrue(AppOpMode.ASK in AppOpMode.choicesFor("10001", AppOpMode.ASK))
        assertFalse(AppOpMode.ASK in AppOpMode.choicesFor("10001", AppOpMode.ALLOW))
    }
}
