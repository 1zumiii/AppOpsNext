package dev.izumi.appopsnext.nativebackend

import dev.izumi.appopsnext.appops.model.ShellCommandResult
import dev.izumi.appopsnext.shizuku.process.RemoteProcessHandle
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.InputStreamReader
import java.io.OutputStreamWriter

internal class NativeDaemonConnection private constructor(
    private val process: RemoteProcessHandle,
    private val reader: BufferedReader,
    private val writer: BufferedWriter,
    val info: NativeDaemonInfo,
) : Closeable {
    private val channel = NativeDaemonChannel(
        exchange = { line ->
            check(process.isAlive()) { "Native daemon is unavailable" }
            writer.write(line)
            writer.flush()
            requireNotNull(reader.readLine()) {
                "Native daemon closed without a command response"
            }
        },
        abort = process::destroy,
    )

    suspend fun execute(command: NativeDaemonCommand): ShellCommandResult =
        NativeDaemonProtocol.decodeResult(channel.request(command.encode()))

    suspend fun ping() {
        check(channel.request(NativeDaemonProtocol.PING_REQUEST) == NativeDaemonProtocol.PING_RESPONSE) {
            "Native daemon returned an invalid probe response"
        }
    }

    override fun close() = channel.close()

    companion object {
        fun open(
            process: RemoteProcessHandle,
        ): NativeDaemonConnection {
            val writer = BufferedWriter(
                OutputStreamWriter(process.stdin, Charsets.UTF_8),
            )
            val reader = BufferedReader(
                InputStreamReader(process.stdout, Charsets.UTF_8),
            )
            writer.write(NativeDaemonProtocol.helloRequest())
            writer.flush()
            val info = NativeDaemonProtocol.parseReady(
                requireNotNull(reader.readLine()) {
                    "Native daemon closed before the handshake completed"
                },
            )
            return NativeDaemonConnection(
                process = process,
                reader = reader,
                writer = writer,
                info = info,
            )
        }
    }
}
