package dev.izumi.appopsnext.nativebackend

import dev.izumi.appopsnext.appops.model.ShellCommandResult
import java.util.Base64

internal object NativeDaemonProtocol {
    const val VERSION = 1
    const val PING_REQUEST = "PING\n"
    const val PING_RESPONSE = "PONG"
    const val EXIT_REQUEST = "EXIT\n"

    fun helloRequest(): String =
        "HELLO $VERSION\n"

    fun parseReady(response: String): NativeDaemonInfo {
        val match = READY_RESPONSE.matchEntire(response)
            ?: error("Native daemon returned an invalid handshake")
        val protocolVersion = match.groupValues[1].toInt()
        checkProtocol(protocolVersion)
        return NativeDaemonInfo(
            protocolVersion = protocolVersion,
            uid = match.groupValues[2].toInt(),
            pid = match.groupValues[3].toInt(),
        )
    }

    fun decodeResult(response: String): ShellCommandResult {
        val fields = response.split(
            '|',
            limit = RESULT_FIELD_COUNT,
        )
        check(
            fields.size == RESULT_FIELD_COUNT &&
                fields[0] == RESULT_PREFIX,
        ) {
            "Native daemon returned an invalid command response"
        }
        return ShellCommandResult(
            exitCode = fields[1].toInt(),
            timedOut = fields[2].toBooleanStrict(),
            stdout = decodeField(fields[3]),
            stderr = decodeField(fields[4]),
        )
    }

    private fun checkProtocol(protocolVersion: Int) {
        check(protocolVersion == VERSION) {
            "Unsupported native daemon protocol: $protocolVersion"
        }
    }

    private fun decodeField(value: String): String =
        if (value == EMPTY_FIELD) {
            ""
        } else {
            String(
                Base64.getDecoder().decode(value),
                Charsets.UTF_8,
            )
        }

    private const val RESULT_PREFIX = "RESULT"
    private const val RESULT_FIELD_COUNT = 5
    private const val EMPTY_FIELD = "-"
    private val READY_RESPONSE =
        Regex("""READY (\d+) (\d+) (\d+)""")
}
