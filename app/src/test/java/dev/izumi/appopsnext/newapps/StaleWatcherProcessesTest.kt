package dev.izumi.appopsnext.newapps

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class StaleWatcherProcessesTest {
    private val name = "dev.izumi.appopsnext:watcher"

    private fun proc(vararg entries: Pair<String, ByteArray?>): File {
        val dir = Files.createTempDirectory("proc").toFile().apply { deleteOnExit() }
        entries.forEach { (entry, cmdline) ->
            val process = File(dir, entry).apply { mkdirs() }
            cmdline?.let { File(process, "cmdline").writeBytes(it) }
        }
        return dir
    }

    private fun padded(value: String) = value.toByteArray() + ByteArray(40)

    @Test
    fun findsOtherProcessesWithTheSameName() {
        val dir = proc(
            "100" to padded(name),
            "200" to padded(name),
            "300" to padded(name),
            "400" to padded("dev.izumi.appopsnext"),
            "500" to "/system/bin/sh\u0000-c\u0000$name".toByteArray(),
            "self" to padded(name),
            "600" to null,
        )

        assertEquals(listOf(100, 300), StaleWatcherProcesses.find(dir, name, ownPid = 200) { true })
    }

    @Test
    fun leavesProcessesOfAnotherOwnerAlone() {
        val dir = proc("100" to padded(name), "300" to padded(name))

        val found = StaleWatcherProcesses.find(dir, name, ownPid = 200) { it.name == "300" }

        assertEquals(listOf(300), found)
    }

    @Test
    fun ownerCheckThatFailsKeepsTheProcess() {
        val dir = proc("100" to padded(name))

        val found = StaleWatcherProcesses.find(dir, name, ownPid = 200) { error("gone") }

        assertEquals(emptyList<Int>(), found)
    }

    @Test
    fun readsTheNameWithoutPadding() {
        assertEquals(name, StaleWatcherProcesses.commandName(name.toByteArray()))
        assertEquals("", StaleWatcherProcesses.commandName(ByteArray(0)))
    }
}
