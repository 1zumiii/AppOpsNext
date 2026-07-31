package dev.izumi.appopsnext.nativebackend

internal object NativeDaemonProtocol {
    const val VERSION = 1
    const val PING_REQUEST = "PING\n"
    const val PING_RESPONSE = "PONG"

    fun helloRequest(token: String): String =
        "HELLO $VERSION $token\n"

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

    private fun checkProtocol(protocolVersion: Int) {
        check(protocolVersion == VERSION) {
            "Unsupported native daemon protocol: $protocolVersion"
        }
    }

    private val READY_RESPONSE =
        Regex("""READY (\d+) (\d+) (\d+)""")
}
