package dev.izumi.appopsnext.presentation.history

import dev.izumi.appopsnext.history.HistorySnapshot
import dev.izumi.appopsnext.history.model.AppOpHistoryFailureReason
import dev.izumi.appopsnext.history.model.HistoryPermission

internal object HistorySnapshotPresentation {
    fun resolve(
        permissions: List<HistoryPermission>,
        snapshots: Map<String, HistorySnapshot>,
        failures: Map<String, AppOpHistoryFailureReason>,
        hideSystemApps: Boolean,
    ): List<PermissionHistory> = permissions.map { permission ->
        val operation = permission.shellOperationName
        val snapshot = snapshots[operation]
        PermissionHistory(
            permission = permission,
            events = snapshot?.events.orEmpty().filter {
                !hideSystemApps || !it.app.isSystemApp
            },
            failureReason = failures[operation],
            lastUpdatedAtMillis = snapshot?.fetchedAtMillis,
        )
    }
}
