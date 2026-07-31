package dev.izumi.appopsnext.shizuku.process

import android.os.ParcelFileDescriptor
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import moe.shizuku.server.IRemoteProcess

/**
 * Small ownership wrapper around Shizuku's internal remote-process Binder.
 *
 * Keeping this type in one package prevents the compatibility backend from
 * leaking Shizuku's deprecated process API into AppOps business code.
 */
class RemoteProcessHandle internal constructor(
    private val remoteProcess: IRemoteProcess,
) {
    val stdin: OutputStream by lazy {
        ParcelFileDescriptor.AutoCloseOutputStream(
            remoteProcess.outputStream,
        )
    }

    val stdout: InputStream by lazy {
        ParcelFileDescriptor.AutoCloseInputStream(
            remoteProcess.inputStream,
        )
    }

    val stderr: InputStream by lazy {
        ParcelFileDescriptor.AutoCloseInputStream(
            remoteProcess.errorStream,
        )
    }

    fun waitFor(
        timeout: Long,
        unit: TimeUnit,
    ): Boolean = remoteProcess.waitForTimeout(timeout, unit.name)

    fun exitValue(): Int = remoteProcess.exitValue()

    fun isAlive(): Boolean = remoteProcess.alive()

    fun destroy() {
        runCatching { remoteProcess.destroy() }
        runCatching { stdin.close() }
        runCatching { stdout.close() }
        runCatching { stderr.close() }
    }
}
