package dev.izumi.appopsnext.presentation.app_detail

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpEntry
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.appops.model.PackageOpsSnapshot

internal object AppOpSnapshotUpdater {
    fun updateMode(
        snapshot: PackageOpsSnapshot,
        operationName: String,
        scope: AppOpScope,
        mode: AppOpMode,
    ): PackageOpsSnapshot {
        val matchingEntryExists = snapshot.entries.any { entry ->
            entry.scope == scope &&
                entry.name.equals(operationName, ignoreCase = true)
        }
        val updatedEntries = snapshot.entries.mapNotNull { entry ->
            if (
                entry.scope == scope &&
                entry.name.equals(operationName, ignoreCase = true)
            ) {
                if (mode == AppOpMode.DEFAULT) null else {
                    entry.copy(mode = mode.shellValue)
                }
            } else {
                entry
            }
        }.let { entries ->
            if (matchingEntryExists || mode == AppOpMode.DEFAULT) {
                entries
            } else {
                entries + AppOpEntry(
                    name = operationName,
                    mode = mode.shellValue,
                    details = null,
                    hasUidModePrefix = scope == AppOpScope.UID,
                )
            }
        }

        return snapshot.copy(entries = updatedEntries)
    }
}
