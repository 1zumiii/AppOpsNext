package dev.izumi.appopsnext.nativebackend

import android.content.Context
import dev.izumi.appopsnext.BuildConfig
import dev.izumi.appopsnext.shizuku.process.RemoteProcessHandle
import dev.izumi.appopsnext.shizuku.process.ShizukuRemoteProcessLauncher
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Installs and launches the native daemon from a Shizuku shell process.
 *
 * Shizuku is used only to create the shell process and transfer its pipe file
 * descriptors. Runtime requests flow through those private kernel pipes,
 * avoiding UserService callbacks and cross-domain sockets.
 */
class NativeDaemonBootstrapper(
    context: Context,
    private val processLauncher: ShizukuRemoteProcessLauncher =
        ShizukuRemoteProcessLauncher(),
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    private val applicationContext = context.applicationContext

    suspend fun launchProbe(): NativeDaemonInfo =
        withContext(Dispatchers.IO) {
            val credentials = createCredentials()
            installDaemon(credentials)
            val process = launchDaemon(credentials)
            try {
                connectAndProbe(process, credentials)
            } catch (error: Throwable) {
                val daemonDetails = readDaemonFailure(process)
                process.destroy()
                throw IOException(
                    buildString {
                        append(error.message ?: "Native daemon probe failed")
                        if (daemonDetails.isNotBlank()) {
                            append("; daemon=")
                            append(daemonDetails)
                        }
                    },
                    error,
                )
            } finally {
                waitForProbeExit(process)
            }
        }

    private fun installDaemon(
        credentials: NativeDaemonCredentials,
    ) {
        val installDirectory =
            "$INSTALL_ROOT/${BuildConfig.VERSION_CODE}"
        val executablePath = "$installDirectory/$DAEMON_FILE_NAME"
        val temporaryPath =
            "$installDirectory/$DAEMON_FILE_NAME.${credentials.instanceId}.tmp"
        val installScript = buildString {
            append("umask 077; ")
            append("mkdir -p ")
            append(installDirectory)
            append(" && cat > ")
            append(temporaryPath)
            append(" && chmod 700 ")
            append(temporaryPath)
            append(" && mv -f ")
            append(temporaryPath)
            append(' ')
            append(executablePath)
        }
        val process = processLauncher.launch(
            arguments = listOf(
                SHELL_BINARY,
                "-c",
                installScript,
            ),
        )

        try {
            applicationContext.assets
                .open(DAEMON_ASSET_PATH)
                .use { daemonAsset ->
                    process.stdin.use { remoteStdin ->
                        daemonAsset.copyTo(remoteStdin)
                    }
                }
        } catch (error: Throwable) {
            process.destroy()
            throw error
        }
        check(
            process.waitFor(
                INSTALL_TIMEOUT_SECONDS,
                TimeUnit.SECONDS,
            ),
        ) {
            process.destroy()
            "Timed out installing the native daemon"
        }
        val stderr = process.stderr
            .bufferedReader()
            .use { it.readText() }
            .trim()
        val exitCode = process.exitValue()
        process.destroy()
        check(exitCode == 0) {
            "Native daemon installation failed with code=$exitCode: $stderr"
        }
    }

    private fun launchDaemon(
        credentials: NativeDaemonCredentials,
    ): RemoteProcessHandle {
        val executablePath =
            "$INSTALL_ROOT/${BuildConfig.VERSION_CODE}/$DAEMON_FILE_NAME"
        return processLauncher.launch(
            arguments = listOf(
                executablePath,
                "--token",
                credentials.token,
            ),
        )
    }

    private fun connectAndProbe(
        process: RemoteProcessHandle,
        credentials: NativeDaemonCredentials,
    ): NativeDaemonInfo {
        val writer = BufferedWriter(
            OutputStreamWriter(process.stdin, Charsets.UTF_8),
        )
        val reader = BufferedReader(
            InputStreamReader(process.stdout, Charsets.UTF_8),
        )

        writer.write(
            NativeDaemonProtocol.helloRequest(credentials.token),
        )
        writer.flush()
        val info = NativeDaemonProtocol.parseReady(
            requireNotNull(reader.readLine()) {
                "Native daemon closed before the handshake completed"
            },
        )

        writer.write(NativeDaemonProtocol.PING_REQUEST)
        writer.flush()
        check(
            reader.readLine() ==
                NativeDaemonProtocol.PING_RESPONSE,
        ) {
            "Native daemon returned an invalid probe response"
        }
        return info
    }

    private fun readDaemonFailure(
        process: RemoteProcessHandle,
    ): String {
        if (process.isAlive()) return ""
        return runCatching {
            process.stderr
                .bufferedReader()
                .use { it.readText() }
                .trim()
                .take(MAX_DAEMON_ERROR_LENGTH)
        }.getOrDefault("")
    }

    private fun waitForProbeExit(process: RemoteProcessHandle) {
        runCatching {
            if (process.isAlive()) {
                process.waitFor(
                    PROBE_EXIT_TIMEOUT_MILLIS,
                    java.util.concurrent.TimeUnit.MILLISECONDS,
                )
            }
        }
        if (process.isAlive()) {
            process.destroy()
        }
    }

    private fun createCredentials(): NativeDaemonCredentials {
        val tokenBytes = ByteArray(TOKEN_BYTE_LENGTH)
        secureRandom.nextBytes(tokenBytes)
        val token = tokenBytes.joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
        val instanceBytes = ByteArray(INSTANCE_ID_BYTE_LENGTH)
        secureRandom.nextBytes(instanceBytes)
        val instanceId = instanceBytes.joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
        return NativeDaemonCredentials(
            token = token,
            instanceId = instanceId,
        )
    }

    private data class NativeDaemonCredentials(
        val token: String,
        val instanceId: String,
    )

    private companion object {
        const val TOKEN_BYTE_LENGTH = 32
        const val INSTANCE_ID_BYTE_LENGTH = 8
        const val MAX_DAEMON_ERROR_LENGTH = 600
        const val INSTALL_TIMEOUT_SECONDS = 10L
        const val PROBE_EXIT_TIMEOUT_MILLIS = 1_000L
        const val INSTALL_ROOT = "/data/local/tmp/appopsnext"
        const val DAEMON_FILE_NAME = "appopsnextd"
        const val DAEMON_ASSET_PATH = "arm64-v8a/appopsnextd"
        const val SHELL_BINARY = "/system/bin/sh"
    }
}
