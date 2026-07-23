package dev.izumi.appops.appops.command

import dev.izumi.appops.appops.model.ShellCommandResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CommandExecutor {
    private val streamExecutor: ExecutorService = Executors.newFixedThreadPool(2)

    fun execute(arguments: List<String>): ShellCommandResult {
        val process = ProcessBuilder(arguments).start()
        val stdout = streamExecutor.submit<String> {
            process.inputStream.bufferedReader().use { it.readText() }
        }
        val stderr = streamExecutor.submit<String> {
            process.errorStream.bufferedReader().use { it.readText() }
        }

        val completed = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
        }

        return ShellCommandResult(
            exitCode = if (completed) process.exitValue() else TIMEOUT_EXIT_CODE,
            stdout = stdout.get(),
            stderr = stderr.get(),
            timedOut = !completed,
        )
    }

    fun close() {
        streamExecutor.shutdownNow()
    }

    private companion object {
        const val COMMAND_TIMEOUT_SECONDS = 10L
        const val TIMEOUT_EXIT_CODE = -1
    }
}
