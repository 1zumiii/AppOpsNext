package dev.izumi.appopsnext.history

import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.history.model.AppOpHistoryEvent
import dev.izumi.appopsnext.presentation.history.ResolvedHistoryEvent
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorySnapshotStoreTest {
    @Test fun `reads beta2 version one snapshots without losing existing records`() = runBlocking {
        withSnapshotFile { file ->
            // A real v1 layout, written independently of the current serializer.
            DataOutputStream(file.outputStream()).use { out ->
                fun string(value: String) {
                    val bytes = value.toByteArray(Charsets.UTF_8)
                    out.writeInt(bytes.size)
                    out.write(bytes)
                }
                out.writeInt(0x48534E50)
                out.writeInt(1)
                out.writeInt(1)
                string("CAMERA")
                out.writeLong(123L)
                out.writeInt(1)
                out.writeInt(10166)
                string("com.example.camera")
                string("CAMERA")
                out.writeBoolean(false)
                out.writeLong(99L)
                out.writeBoolean(false)
                string("top")
                string("s")
                out.writeInt(2)
                out.writeBoolean(true)
                string("Camera")
                string("com.example.camera")
                out.writeInt(10166)
                out.writeBoolean(false)
            }
            val loaded = HistorySnapshotStore(file).read().getValue("CAMERA")
            assertEquals(123L, loaded.fetchedAtMillis)
            assertEquals(2, loaded.events.single().event.accessCount)
            assertEquals(0, loaded.events.single().event.rejectCount)
            assertEquals(null, loaded.events.single().event.intervalStartTimeMillis)
        }
    }
    @Test fun `staging updates memory and one flush persists the full batch`() = runBlocking {
        withSnapshotFile { file ->
            val store = HistorySnapshotStore(file)
            val camera = HistorySnapshot(listOf(resolvedEvent()), 1L)
            val audio = HistorySnapshot(emptyList(), 2L)
            store.stage("CAMERA", camera)
            store.stage("RECORD_AUDIO", audio)
            assertEquals(mapOf("CAMERA" to camera, "RECORD_AUDIO" to audio), store.read())
            assertTrue(!file.exists())
            store.flush()
            assertEquals(store.read(), HistorySnapshotStore(file).read())
        }
    }

    @Test fun `failed flush remains pending and can be retried without staging again`() = runBlocking {
        withSnapshotFile { parent ->
            parent.writeText("blocks directory creation")
            val file = File(parent, "snapshot.bin")
            val store = HistorySnapshotStore(file)
            store.stage("CAMERA", HistorySnapshot(emptyList(), 1L))
            store.flush()
            assertTrue(!file.exists())
            parent.delete()
            parent.mkdir()
            store.flush()
            assertEquals(store.read(), HistorySnapshotStore(file).read())
            file.delete()
        }
    }

    @Test fun `cancelled flush leaves staged snapshots available for a later flush`() = runBlocking {
        withSnapshotFile { file ->
            val store = HistorySnapshotStore(file)
            store.stage("CAMERA", HistorySnapshot(emptyList(), 1L))
            launch {
                coroutineContext.cancel()
                store.flush()
            }.join()
            store.flush()
            assertEquals(store.read(), HistorySnapshotStore(file).read())
        }
    }

    @Test fun `round trips all resolved event fields through a new store instance`() = runBlocking {
        withSnapshotFile { file ->
            val snapshot = HistorySnapshot(
                events = listOf(
                    resolvedEvent(attributionTag = "capture", durationMillis = null),
                    resolvedEvent(uid = 43, attributionTag = null, durationMillis = 321L),
                ),
                fetchedAtMillis = 123_456L,
            )
            HistorySnapshotStore(file).put("CAMERA", snapshot)

            assertEquals(mapOf("CAMERA" to snapshot), HistorySnapshotStore(file).read())
        }
    }

    @Test fun `corrupt or unsupported snapshots fall back to empty history`() = runBlocking {
        withSnapshotFile { file ->
            file.writeBytes(byteArrayOf(1, 2, 3))
            assertTrue(HistorySnapshotStore(file).read().isEmpty())

            DataOutputStream(file.outputStream()).use {
                it.writeInt(0x48534E50)
                it.writeInt(999)
            }
            assertTrue(HistorySnapshotStore(file).read().isEmpty())
        }
    }

    @Test fun `successful empty snapshot replaces old operation history`() = runBlocking {
        withSnapshotFile { file ->
            val store = HistorySnapshotStore(file)
            store.put("CAMERA", HistorySnapshot(listOf(resolvedEvent()), 1L))
            store.put("CAMERA", HistorySnapshot(emptyList(), 2L))

            assertEquals(mapOf("CAMERA" to HistorySnapshot(emptyList(), 2L)), HistorySnapshotStore(file).read())
        }
    }

    @Test fun `updating an operation preserves snapshots for other operations`() = runBlocking {
        withSnapshotFile { file ->
            val store = HistorySnapshotStore(file)
            val microphone = HistorySnapshot(listOf(resolvedEvent(operation = "RECORD_AUDIO")), 1L)
            val camera = HistorySnapshot(listOf(resolvedEvent(operation = "CAMERA", uid = 88)), 2L)
            store.put("RECORD_AUDIO", microphone)
            store.put("CAMERA", camera)
            val replacement = HistorySnapshot(emptyList(), 3L)
            store.put("CAMERA", replacement)

            assertEquals(
                mapOf("RECORD_AUDIO" to microphone, "CAMERA" to replacement),
                HistorySnapshotStore(file).read(),
            )
        }
    }

    private fun resolvedEvent(
        operation: String = "CAMERA",
        uid: Int = 42,
        attributionTag: String? = "capture",
        durationMillis: Long? = null,
    ) = ResolvedHistoryEvent(
        event = AppOpHistoryEvent(
            uid = uid,
            packageName = "dev.izumi.example",
            operationName = operation,
            attributionTag = attributionTag,
            accessTimeMillis = 99L,
            durationMillis = durationMillis,
            uidState = "TOP",
            flags = "SELF",
            accessCount = 7,
            isAggregated = true,
            rejectCount = 5,
            intervalStartTimeMillis = 50L,
        ),
        app = InstalledApp(
            label = "Example",
            packageName = "dev.izumi.example",
            uid = uid,
            isSystemApp = true,
        ),
    )

    private suspend fun withSnapshotFile(block: suspend (File) -> Unit) {
        val directory = Files.createTempDirectory("history-snapshot-test").toFile()
        try {
            block(File(directory, "history.bin"))
        } finally {
            directory.listFiles()?.forEach(File::delete)
            directory.delete()
        }
    }
}
