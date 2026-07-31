package dev.izumi.appopsnext.nativebackend

import dev.izumi.appopsnext.appops.model.ShellCommandResult
import dev.izumi.appopsnext.shizuku.process.RemoteProcessHandle
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class NativeDaemonConnection private constructor(
    private val process: RemoteProcessHandle,
    private val reader: BufferedReader,
    private val writer: BufferedWriter,
    val info: NativeDaemonInfo,
) : Closeable {
    private val ioLock = Any()
    @Volatile
    private var closed = false

    suspend fun execute(
        command: NativeDaemonCommand,
    ): ShellCommandResult =
        withContext(Dispatchers.IO) {
            synchronized(ioLock) {
                check(!closed && process.isAlive()) {
                    "Native daemon is unavailable"
                }
                writer.write(command.encode())
                writer.flush()
                NativeDaemonProtocol.decodeResult(
                    requireNotNull(reader.readLine()) {
                        "Native daemon closed without a command response"
                    },
                )
            }
        }

    suspend fun ping() {
        withContext(Dispatchers.IO) {
            synchronized(ioLock) {
                check(!closed && process.isAlive()) {
                    "Native daemon is unavailable"
                }
                writer.write(NativeDaemonProtocol.PING_REQUEST)
                writer.flush()
                check(
                    reader.readLine() ==
                        NativeDaemonProtocol.PING_RESPONSE,
                ) {
                    "Native daemon returned an invalid probe response"
                }
            }
        }
    }

    override fun close() {
        synchronized(ioLock) {
            if (closed) return
            closed = true
            runCatching {
                if (process.isAlive()) {
                    writer.write(NativeDaemonProtocol.EXIT_REQUEST)
                    writer.flush()
                }
            }
            process.destroy()
        }
    }

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
