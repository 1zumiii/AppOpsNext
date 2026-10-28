package dev.izumi.appopsnext.guard

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuardCodecTest {
    @Test fun `rules, events and states round trip`() {
        val rule = GuardRule("com.example", 1010123, "android:run_any_in_background", AppOpScope.UID, AppOpMode.IGNORE, true)
        assertEquals(rule, GuardCodec.decodeRule(GuardCodec.encodeRule(rule)))
        val event = GuardEvent(42, "com.example", "android:camera", AppOpScope.PACKAGE, AppOpMode.IGNORE, AppOpMode.ALLOW, GuardEventKind.RESTORE_LIMIT)
        assertEquals(event, GuardCodec.decodeEvent(GuardCodec.encodeEvent(event)))
        for (state in listOf(GuardRuleState(), GuardRuleState(AppOpMode.FOREGROUND, listOf(1, 2, 3)))) {
            assertEquals(rule.id to state, GuardCodec.decodeState(GuardCodec.encodeState(rule.id, state)))
        }
    }

    @Test fun `malformed records are dropped`() {
        assertNull(GuardCodec.decodeRule("R1\tcom.example\tx\tandroid:camera\tPACKAGE\tIGNORE\tfalse"))
        assertNull(GuardCodec.decodeRule("R2\tcom.example\t1\tandroid:camera\tPACKAGE\tIGNORE\tfalse"))
        assertNull(GuardCodec.decodeEvent("E1\t1\tp\tandroid:camera\tPACKAGE\tIGNORE\tSOMETIMES\tDRIFT"))
        assertNull(GuardCodec.decodeState("S1\tid\tIGNORE\tsoon"))
    }
}
