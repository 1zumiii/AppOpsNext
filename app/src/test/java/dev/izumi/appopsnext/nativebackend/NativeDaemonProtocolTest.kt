package dev.izumi.appopsnext.nativebackend

import dev.izumi.appopsnext.appops.model.ShellCommandResult
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NativeDaemonProtocolTest {
    @Test
    fun `hello request includes protocol version`() {
        assertEquals(
            "HELLO 1\n",
            NativeDaemonProtocol.helloRequest(),
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

    @Test
    fun `command response decodes shell result`() {
        val stdout = Base64.getEncoder().encodeToString(
            "android:camera: allow\n".toByteArray(),
        )
        assertEquals(
            ShellCommandResult(
                exitCode = 0,
                stdout = "android:camera: allow\n",
                stderr = "",
                timedOut = false,
            ),
            NativeDaemonProtocol.decodeResult(
                "RESULT|0|false|$stdout|-",
            ),
        )
    }

    @Test
    fun `malformed command response is rejected`() {
        assertThrows(IllegalStateException::class.java) {
            NativeDaemonProtocol.decodeResult(
                "PONG",
            )
        }
    }

    @Test
    fun `daemon command rejects whitespace arguments`() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeDaemonCommand(
                verb = "GET_PACKAGE_OPS",
                arguments = listOf("package; id"),
            )
        }
    }
}
