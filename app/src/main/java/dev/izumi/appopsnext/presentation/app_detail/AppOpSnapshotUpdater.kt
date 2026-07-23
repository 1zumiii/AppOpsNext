package dev.izumi.appopsnext.presentation.app_detail

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.appops.model.PackageOpsSnapshot

internal object AppOpSnapshotUpdater {
    fun updateMode(
        snapshot: PackageOpsSnapshot,
        operationName: String,
        scope: AppOpScope,
        mode: AppOpMode,
    ): PackageOpsSnapshot =
        snapshot.copy(
            entries = snapshot.entries.map { entry ->
                if (
                    entry.scope == scope &&
                    entry.name.equals(operationName, ignoreCase = true)
                ) {
                    entry.copy(mode = mode.shellValue)
                } else {
                    entry
                }
            },
        )
}
