package dev.izumi.appopsnext.nativebackend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NativeDaemonProtocolTest {
    @Test
    fun `hello request includes protocol and token`() {
        assertEquals(
            "HELLO 1 abc123\n",
            NativeDaemonProtocol.helloRequest("abc123"),
        )
    }

    @Test
    fun `ready response exposes daemon identity`() {
        assertEquals(
            NativeDaemonInfo(
                protocolVersion = 1,
                uid = 2_000,
                pid = 12_345,
            ),
            NativeDaemonProtocol.parseReady(
                "READY 1 2000 12345",
            ),
        )
    }

    @Test
    fun `unsupported daemon protocol is rejected`() {
        assertThrows(IllegalStateException::class.java) {
            NativeDaemonProtocol.parseReady(
                "READY 2 2000 12345",
            )
        }
    }

    @Test
    fun `malformed daemon handshake is rejected`() {
        assertThrows(IllegalStateException::class.java) {
            NativeDaemonProtocol.parseReady(
                "READY uid=2000",
            )
        }
    }
}
