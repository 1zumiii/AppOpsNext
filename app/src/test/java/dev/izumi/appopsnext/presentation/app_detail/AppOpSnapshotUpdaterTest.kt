package dev.izumi.appopsnext.presentation.app_detail

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpEntry
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.appops.model.PackageOpsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class AppOpSnapshotUpdaterTest {
    @Test
    fun `mode update changes only the requested scope`() {
        val snapshot = PackageOpsSnapshot(
            packageName = "dev.izumi.example",
            entries = listOf(
                entry("CAMERA", "foreground", AppOpScope.UID),
                entry("CAMERA", "allow", AppOpScope.PACKAGE),
                entry("RECORD_AUDIO", "ignore", AppOpScope.UID),
            ),
            rawOutput = "diagnostic raw output",
        )

        val updated = AppOpSnapshotUpdater.updateMode(
            snapshot = snapshot,
            operationName = "camera",
            scope = AppOpScope.UID,
            mode = AppOpMode.IGNORE,
        )

        assertEquals(
            listOf("ignore", "allow", "ignore"),
            updated.entries.map(AppOpEntry::mode),
        )
        assertEquals(snapshot.rawOutput, updated.rawOutput)
    }

    @Test
    fun `mode update materializes a previously implicit operation`() {
        val snapshot = PackageOpsSnapshot(
            packageName = "dev.izumi.example",
            entries = emptyList(),
            rawOutput = "No operations.",
        )

        val updated = AppOpSnapshotUpdater.updateMode(
            snapshot = snapshot,
            operationName = "READ_CLIPBOARD",
            scope = AppOpScope.PACKAGE,
            mode = AppOpMode.IGNORE,
        )

        assertEquals(1, updated.entries.size)
        assertEquals("READ_CLIPBOARD", updated.entries.single().name)
        assertEquals("ignore", updated.entries.single().mode)
        assertEquals(AppOpScope.PACKAGE, updated.entries.single().scope)
    }

    @Test
    fun `restoring default removes the explicit scoped operation`() {
        val snapshot = PackageOpsSnapshot(
            packageName = "dev.izumi.example",
            entries = listOf(
                entry("CAMERA", "ignore", AppOpScope.PACKAGE),
                entry("CAMERA", "foreground", AppOpScope.UID),
            ),
            rawOutput = "diagnostic raw output",
        )

        val updated = AppOpSnapshotUpdater.updateMode(
            snapshot = snapshot,
            operationName = "CAMERA",
            scope = AppOpScope.PACKAGE,
            mode = AppOpMode.DEFAULT,
        )

        assertEquals(1, updated.entries.size)
        assertEquals(AppOpScope.UID, updated.entries.single().scope)
    }

    private fun entry(
        name: String,
        mode: String,
        scope: AppOpScope,
    ) = AppOpEntry(
        name = name,
        mode = mode,
        details = null,
        hasUidModePrefix = scope == AppOpScope.UID,
    )
}
