package dev.izumi.appopsnext.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorTargetsCodecTest {
    @Test fun `an absent or blank selection decodes to nothing watched`() {
        assertTrue(MonitorTargetsCodec.decode(null).isEmpty())
        assertTrue(MonitorTargetsCodec.decode("").isEmpty())
        assertTrue(MonitorTargetsCodec.decode("   \n  ").isEmpty())
    }

    @Test fun `round trips packages and their operations`() {
        val targets = listOf(
            MonitorTarget("dev.izumi.one", setOf("android:camera", "android:record_audio")),
            MonitorTarget("dev.izumi.two", setOf("android:read_sms")),
        )
        assertEquals(targets, MonitorTargetsCodec.decode(MonitorTargetsCodec.encode(targets)))
    }

    @Test fun `a package without operations is dropped rather than watched wholesale`() {
        assertTrue(MonitorTargetsCodec.decode("dev.izumi.one").isEmpty())
        assertEquals("", MonitorTargetsCodec.encode(listOf(MonitorTarget("a", emptySet()))))
    }

    @Test fun `malformed lines are skipped without losing the rest`() {
        val decoded = MonitorTargetsCodec.decode(
            "\ndev.izumi.one android:camera\n   \nbroken\ndev.izumi.two android:read_sms,\n",
        )
        assertEquals(
            listOf(
                MonitorTarget("dev.izumi.one", setOf("android:camera")),
                MonitorTarget("dev.izumi.two", setOf("android:read_sms")),
            ),
            decoded,
        )
    }

    /** An intermediate build wrote intervals here; the selection still reads. */
    @Test
    fun `an operation carrying an interval still decodes as watched`() {
        val decoded = MonitorTargetsCodec
            .decode("example.app android:fine_location@15,android:read_sms repeat")
            .single()
        assertEquals(
            setOf("android:fine_location", "android:read_sms"),
            decoded.operationNames,
        )
        assertEquals(
            "example.app android:fine_location,android:read_sms",
            MonitorTargetsCodec.encode(listOf(decoded)),
        )
    }
}
