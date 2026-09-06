package dev.izumi.appopsnext.presentation.history

import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.history.HistorySnapshot
import dev.izumi.appopsnext.history.model.AppOpHistoryEvent
import dev.izumi.appopsnext.history.model.AppOpHistoryFailureReason
import dev.izumi.appopsnext.history.model.HistoryPermission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistorySnapshotPresentationTest {
    private val camera = HistoryPermission("CAMERA")
    private val microphone = HistoryPermission("RECORD_AUDIO")
    private val userApp = InstalledApp("User", "com.example.user", 10_001, false)
    private val systemApp = InstalledApp("System", "com.example.system", 10_002, true)

    @Test
    fun `cached events and original timestamp survive a refresh failure`() {
        val event = resolvedEvent(userApp, "CAMERA", accessTimeMillis = 123L)

        val result = HistorySnapshotPresentation.resolve(
            permissions = listOf(camera),
            snapshots = mapOf("CAMERA" to HistorySnapshot(listOf(event), fetchedAtMillis = 456L)),
            failures = mapOf("CAMERA" to AppOpHistoryFailureReason.COMMAND_FAILED),
            hideSystemApps = false,
        )

        assertEquals(listOf(event), result.single().events)
        assertEquals(456L, result.single().lastUpdatedAtMillis)
        assertEquals(AppOpHistoryFailureReason.COMMAND_FAILED, result.single().failureReason)
    }

    @Test
    fun `missing snapshot is distinct from a successful empty snapshot`() {
        val result = HistorySnapshotPresentation.resolve(
            permissions = listOf(camera, microphone),
            snapshots = mapOf("CAMERA" to HistorySnapshot(emptyList(), fetchedAtMillis = 789L)),
            failures = emptyMap(),
            hideSystemApps = false,
        )

        assertEquals(emptyList<ResolvedHistoryEvent>(), result[0].events)
        assertEquals(789L, result[0].lastUpdatedAtMillis)
        assertEquals(emptyList<ResolvedHistoryEvent>(), result[1].events)
        assertNull(result[1].lastUpdatedAtMillis)
    }

    @Test
    fun `system app filter is reversible without mutating the cached snapshot`() {
        val events = listOf(
            resolvedEvent(userApp, "CAMERA", accessTimeMillis = 1L),
            resolvedEvent(systemApp, "CAMERA", accessTimeMillis = 2L),
        )
        val snapshots = mapOf("CAMERA" to HistorySnapshot(events, fetchedAtMillis = 3L))

        val hidden = HistorySnapshotPresentation.resolve(
            permissions = listOf(camera),
            snapshots = snapshots,
            failures = emptyMap(),
            hideSystemApps = true,
        )
        val shown = HistorySnapshotPresentation.resolve(
            permissions = listOf(camera),
            snapshots = snapshots,
            failures = emptyMap(),
            hideSystemApps = false,
        )

        assertEquals(listOf(userApp.packageName), hidden.single().events.map { it.app.packageName })
        assertEquals(
            listOf(userApp.packageName, systemApp.packageName),
            shown.single().events.map { it.app.packageName },
        )
        assertEquals(events, snapshots.getValue("CAMERA").events)
    }

    @Test
    fun `presentation follows selected permission order and omits removed selection`() {
        val result = HistorySnapshotPresentation.resolve(
            permissions = listOf(microphone, camera),
            snapshots = mapOf(
                "CAMERA" to HistorySnapshot(emptyList(), fetchedAtMillis = 1L),
                "RECORD_AUDIO" to HistorySnapshot(emptyList(), fetchedAtMillis = 2L),
            ),
            failures = emptyMap(),
            hideSystemApps = false,
        )

        assertEquals(listOf(microphone, camera), result.map { it.permission })
        assertEquals(listOf(2L, 1L), result.map { it.lastUpdatedAtMillis })
    }

    private fun resolvedEvent(
        app: InstalledApp,
        operationName: String,
        accessTimeMillis: Long,
    ) = ResolvedHistoryEvent(
        event = AppOpHistoryEvent(
            uid = app.uid,
            packageName = app.packageName,
            operationName = operationName,
            attributionTag = null,
            accessTimeMillis = accessTimeMillis,
            durationMillis = null,
            uidState = "top",
            flags = "s",
        ),
        app = app,
    )
}
