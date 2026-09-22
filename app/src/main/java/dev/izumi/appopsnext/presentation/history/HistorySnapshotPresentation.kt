package dev.izumi.appopsnext.presentation.history

import dev.izumi.appopsnext.history.ArchivedHistory
import dev.izumi.appopsnext.history.HistorySnapshot
import dev.izumi.appopsnext.history.model.AppOpHistoryFailureReason
import dev.izumi.appopsnext.history.model.HistoryPermission

internal object HistorySnapshotPresentation {
    fun resolve(
        permissions: List<HistoryPermission>,
        snapshots: Map<String, HistorySnapshot>,
        failures: Map<String, AppOpHistoryFailureReason>,
        hideSystemApps: Boolean,
        /** Null while saving individual records is off: the archive is then ignored. */
        archive: Map<String, ArchivedHistory>? = null,
    ): List<PermissionHistory> = permissions.map { permission ->
        val operation = permission.shellOperationName
        val snapshot = snapshots[operation]
        val events = snapshot?.events.orEmpty().let { system ->
            if (archive == null) system else HistoryArchiveMerger.merge(system, archive[operation])
        }
        PermissionHistory(
            permission = permission,
            events = events.filter {
                !hideSystemApps || !it.app.isSystemApp
            },
            failureReason = failures[operation],
            lastUpdatedAtMillis = snapshot?.fetchedAtMillis,
            individualRecordsAvailable = events.any { !it.event.isAggregated },
        )
    }
}
