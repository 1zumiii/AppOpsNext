package dev.izumi.appopsnext.guard

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardCodecTest {
    @Test fun `rules, events and states round trip`() {
        val rule = GuardRule("com.example", 1010123, "android:run_any_in_background", AppOpScope.UID, AppOpMode.IGNORE, true)
        assertEquals(rule, GuardCodec.decodeRule(GuardCodec.encodeRule(rule)))
        val event = GuardEvent(42, "com.example", "android:camera", AppOpScope.PACKAGE, AppOpMode.IGNORE, AppOpMode.ALLOW, GuardEventKind.RESTORE_LIMIT)
        assertEquals(event, GuardCodec.decodeEvent(GuardCodec.encodeEvent(event)))
        for (state in listOf(GuardRuleState(), GuardRuleState(AppOpMode.FOREGROUND, listOf(1, 2, 3), driftSince = 1))) {
            assertEquals(rule.id to state, GuardCodec.decodeState(GuardCodec.encodeState(rule.id, state)))
        }
    }

    @Test fun `a rule with a delay round trips, and one without stays readable by older versions`() {
        val rule = GuardRule("com.example", 10123, "android:camera", AppOpScope.PACKAGE, AppOpMode.IGNORE, true)
        assertTrue(GuardCodec.encodeRule(rule).startsWith("R1\t"))
        val delayed = rule.copy(restoreDelayMinutes = 15)
        assertTrue(GuardCodec.encodeRule(delayed).startsWith("R2\t"))
        assertEquals(delayed, GuardCodec.decodeRule(GuardCodec.encodeRule(delayed)))
        assertNull(GuardCodec.decodeRule("R2\tcom.example\t1\tandroid:camera\tPACKAGE\tIGNORE\ttrue\t1441"))
        val leaving = rule.copy(restoreAfterLeaving = true)
        assertTrue(GuardCodec.encodeRule(leaving).startsWith("R2\t"))
        assertEquals(leaving, GuardCodec.decodeRule(GuardCodec.encodeRule(leaving)))
        // Written by builds after 1.6.8-beta1, before the app could be waited for.
        assertEquals(delayed, GuardCodec.decodeRule("R2\tcom.example\t10123\tandroid:camera\tPACKAGE\tIGNORE\ttrue\t15"))
        assertNull(GuardCodec.decodeRule("R2\tcom.example\t1\tandroid:camera\tPACKAGE\tIGNORE\ttrue\t15\tmaybe"))
    }

    @Test fun `states keep when a change was first seen and older states still read`() {
        for (state in listOf(
            GuardRuleState(AppOpMode.ALLOW, listOf(5, 6), driftSince = 4),
            GuardRuleState(AppOpMode.ALLOW, listOf(5), driftSince = 4, heldOnScreen = true),
        )) {
            assertEquals("id" to state, GuardCodec.decodeState(GuardCodec.encodeState("id", state)))
        }
        assertEquals(
            "id" to GuardRuleState(AppOpMode.ALLOW, listOf(5, 6)),
            GuardCodec.decodeState("S1\tid\tALLOW\t5\t6"),
        )
        assertNull(GuardCodec.decodeState("S2\tid\tALLOW\tsoon\t-"))
        assertNull(GuardCodec.decodeState("S2\tid\tALLOW\t4\t5"))
    }

    @Test fun `malformed records are dropped`() {
        assertNull(GuardCodec.decodeRule("R1\tcom.example\tx\tandroid:camera\tPACKAGE\tIGNORE\tfalse"))
        assertNull(GuardCodec.decodeRule("R2\tcom.example\t1\tandroid:camera\tPACKAGE\tIGNORE\tfalse"))
        assertNull(GuardCodec.decodeRule("R1\tcom.example;rm\t1\tandroid:camera\tPACKAGE\tIGNORE\tfalse"))
        assertNull(GuardCodec.decodeRule("R1\tcom.example\t1\tandroid:cam era\tPACKAGE\tIGNORE\tfalse"))
        assertNull(GuardCodec.decodeEvent("E1\t1\tp\tandroid:camera\tPACKAGE\tIGNORE\tSOMETIMES\tDRIFT"))
        assertNull(GuardCodec.decodeState("S1\tid\tIGNORE\tsoon"))
    }
}
