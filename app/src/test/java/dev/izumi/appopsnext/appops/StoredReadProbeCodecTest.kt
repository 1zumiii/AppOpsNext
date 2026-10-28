package dev.izumi.appopsnext.appops

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoredReadProbeCodecTest {
    @Test fun `round trip keeps capabilities, time and notes`() {
        val probe = StoredReadProbe(
            buildKey = "google/raven/raven:17/AP4A/1:user/release-keys#42",
            capabilities = setOf(AppOpsReadCapability.PACKAGE_OPS, AppOpsReadCapability.OP_LIST),
            probedAtMillis = 1_761_700_000_000,
            notes = listOf("opCount=150", "opStates=mismatch"),
        )
        assertEquals(probe, StoredReadProbeCodec.decode(StoredReadProbeCodec.encode(probe)))
        val empty = probe.copy(capabilities = emptySet(), notes = emptyList())
        assertEquals(empty, StoredReadProbeCodec.decode(StoredReadProbeCodec.encode(empty)))
    }

    @Test fun `unknown version or capability is not trusted`() {
        assertNull(StoredReadProbeCodec.decode(null))
        assertNull(StoredReadProbeCodec.decode("v0\nkey\nPACKAGE_OPS\n1"))
        assertNull(StoredReadProbeCodec.decode("v1\nkey\nFUTURE_READ\n1"))
        assertNull(StoredReadProbeCodec.decode("v1\nkey\nPACKAGE_OPS\nlater"))
    }
}
