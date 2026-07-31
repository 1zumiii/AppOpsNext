package dev.izumi.appopsnext.nativebackend

import android.content.Context
import dev.izumi.appopsnext.BuildConfig
import dev.izumi.appopsnext.shizuku.process.RemoteProcessHandle
import dev.izumi.appopsnext.shizuku.process.ShizukuRemoteProcessLauncher
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.Executors
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

    internal suspend fun launch(): NativeDaemonConnection =
        withContext(Dispatchers.IO) {
            val credentials = createCredentials()
            installDaemon(credentials)
            val process = launchDaemon(credentials)
            try {
                openConnection(process)
            } catch (error: Throwable) {
                val daemonDetails = readDaemonFailure(process)
                process.destroy()
                throw IOException(
                    buildString {
                        append(error.message ?: "Native daemon startup failed")
                        if (daemonDetails.isNotBlank()) {
                            append("; daemon=")
                            append(daemonDetails)
                        }
                    },
                    error,
                )
            }
        }

    private fun openConnection(
        process: RemoteProcessHandle,
    ): NativeDaemonConnection {
        val executor = Executors.newSingleThreadExecutor()
        return try {
            executor.submit<NativeDaemonConnection> {
                NativeDaemonConnection.open(process)
            }.get(HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
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
            ),
        )
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

    private fun createCredentials(): NativeDaemonCredentials {
        val instanceBytes = ByteArray(INSTANCE_ID_BYTE_LENGTH)
        secureRandom.nextBytes(instanceBytes)
        val instanceId = instanceBytes.joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
        return NativeDaemonCredentials(
            instanceId = instanceId,
        )
    }

    private data class NativeDaemonCredentials(
        val instanceId: String,
    )

    private companion object {
        const val INSTANCE_ID_BYTE_LENGTH = 8
        const val MAX_DAEMON_ERROR_LENGTH = 600
        const val INSTALL_TIMEOUT_SECONDS = 10L
        const val HANDSHAKE_TIMEOUT_SECONDS = 5L
        const val INSTALL_ROOT = "/data/local/tmp/appopsnext"
        const val DAEMON_FILE_NAME = "appopsnextd"
        const val DAEMON_ASSET_PATH = "arm64-v8a/appopsnextd"
        const val SHELL_BINARY = "/system/bin/sh"
    }
}
