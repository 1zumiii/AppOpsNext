package dev.izumi.appopsnext.shizuku.process

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ShizukuProcessProbeResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

class ShizukuProcessCapabilityProbe(
    private val launcher: ShizukuRemoteProcessLauncher =
        ShizukuRemoteProcessLauncher(),
) {
    suspend fun run(): ShizukuProcessProbeResult =
        withContext(Dispatchers.IO) {
            val process = launcher.launch(
                arguments = listOf("/system/bin/id"),
            )
            try {
                check(process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    "Shizuku remote-process probe timed out"
                }
                ShizukuProcessProbeResult(
                    exitCode = process.exitValue(),
                    stdout = process.stdout.bufferedReader().use { it.readText() },
                    stderr = process.stderr.bufferedReader().use { it.readText() },
                )
            } finally {
                process.destroy()
            }
        }

    private companion object {
        const val PROBE_TIMEOUT_SECONDS = 5L
    }
}
