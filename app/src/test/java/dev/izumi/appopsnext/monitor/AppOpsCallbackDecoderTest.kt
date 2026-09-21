package dev.izumi.appopsnext.monitor

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class AppOpsCallbackDecoderTest {
    private fun input(kind: AppOpAccessKind, modern: Boolean, mode: Int = 0, active: Int = 1,
                      tailAdjustment: Int = 0): AppOpsCallbackInput {
        val buffer = ByteBuffer.allocate(256)
        buffer.putInt(29).putInt(10001)
        for (text in listOf("example.app", "tag")) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            buffer.putInt(bytes.size).put(bytes)
        }
        if (modern) buffer.putInt(7)
        when (kind) {
            AppOpAccessKind.ACTIVE -> buffer.putInt(active).putInt(1).putInt(-1)
            AppOpAccessKind.NOTED -> buffer.putInt(1).putInt(mode)
            AppOpAccessKind.STARTED -> buffer.putInt(1).putInt(mode).putInt(1).putInt(0).putInt(-1)
        }
        buffer.limit(buffer.position() + tailAdjustment)
        buffer.position(0)
        return object : AppOpsCallbackInput {
            override val remaining: Int get() = buffer.remaining()
            override fun int(): Int = buffer.int
            override fun string(): String = ByteArray(int()).also(buffer::get).toString(Charsets.UTF_8)
        }
    }

    @Test fun `both layouts preserve noted outcomes`() {
        for (modern in listOf(false, true)) for (mode in 0..4) {
            val event = AppOpsCallbackDecoder.decode(AppOpAccessKind.NOTED,
                input(AppOpAccessKind.NOTED, modern, mode))!!
            assertEquals(mode == 0, event.allowed)
            assertEquals(10001, event.uid)
        }
    }

    @Test fun `allowed started is retained in both layouts`() {
        for (modern in listOf(false, true)) for (mode in 0..4) {
            val event = AppOpsCallbackDecoder.decode(AppOpAccessKind.STARTED,
                input(AppOpAccessKind.STARTED, modern, mode))!!
            assertEquals(mode == 0, event.allowed)
        }
    }

    @Test fun `active stop is valid ignored input not a parse failure`() {
        for (modern in listOf(false, true)) {
            assertNull(AppOpsCallbackDecoder.decode(AppOpAccessKind.ACTIVE,
                input(AppOpAccessKind.ACTIVE, modern, active = 0)))
            assertTrue(AppOpsCallbackDecoder.decode(AppOpAccessKind.ACTIVE,
                input(AppOpAccessKind.ACTIVE, modern))!!.allowed)
        }
    }

    @Test fun `invalid tail sizes reject truncated and extended payloads`() {
        for (kind in AppOpAccessKind.entries) for (modern in listOf(false, true)) {
            for (adjustment in listOf(-3, -2, -1, 1, 2, 3, 8)) {
                assertTrue("$kind modern=$modern adjustment=$adjustment",
                    runCatching { AppOpsCallbackDecoder.decode(kind,
                        input(kind, modern, tailAdjustment = adjustment)) }.isFailure)
            }
        }
    }

    @Test fun `invalid mode and boolean are rejected`() {
        assertTrue(runCatching { AppOpsCallbackDecoder.decode(AppOpAccessKind.NOTED,
            input(AppOpAccessKind.NOTED, true, mode = -1)) }.isFailure)
        assertTrue(runCatching { AppOpsCallbackDecoder.decode(AppOpAccessKind.ACTIVE,
            input(AppOpAccessKind.ACTIVE, true, active = 77)) }.isFailure)
    }

    /**
     * MIUI and other vendor images add modes above MODE_FOREGROUND. Rejecting the
     * whole event would silence the monitor on those devices, and a mode we do not
     * recognise is not an allowed one.
     */
    @Test fun `vendor modes are reported as not allowed rather than dropped`() {
        for (kind in listOf(AppOpAccessKind.NOTED, AppOpAccessKind.STARTED)) {
            for (mode in listOf(5, 77)) {
                val event = AppOpsCallbackDecoder.decode(kind, input(kind, true, mode))
                assertNotNull("$kind mode=$mode", event)
                assertFalse("$kind mode=$mode", event!!.allowed)
            }
        }
    }
}
