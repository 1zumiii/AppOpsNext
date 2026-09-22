package dev.izumi.appopsnext.presentation.history

import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.history.model.AppOpHistoryEvent
import dev.izumi.appopsnext.history.model.AppOpHistoryFailureReason
import dev.izumi.appopsnext.history.model.HistoryPermission

data class HistoryUiState(
    val isLoading: Boolean = false,
    val waitingForBackend: Boolean = true,
    val permissions: List<PermissionHistory> = emptyList(),
    val availablePermissions: List<HistoryPermission> = emptyList(),
    val failureReason: AppOpHistoryFailureReason? = null,
    val partialFailureCount: Int = 0,
    val lastUpdatedAtMillis: Long? = null,
    val autoRefreshIntervalMinutes: Int = 5,
    /** Saved individual records can reach past 30 days, so the page offers "all". */
    val saveIndividualHistory: Boolean = false,
)

data class PermissionHistory(
    val permission: HistoryPermission,
    val events: List<ResolvedHistoryEvent>,
    val lastUpdatedAtMillis: Long? = null,
    val failureReason: AppOpHistoryFailureReason? = null,
    /**
     * Whether the system keeps individual records for this operation. Decided on
     * the whole history, so a filtered subset keeps counting in the same unit.
     */
    val individualRecordsAvailable: Boolean = false,
) {
    /**
     * Individual records follow the system's own merging, while an interval counts every
     * access the system logged, so the two are never added together.
     */
    val recordCount: Int
        get() = events.filter { !individualRecordsAvailable || !it.event.isAggregated }
            .sumOf { it.event.accessCount }

    /** Interval accesses filling gaps between individual records, counted apart. */
    val intervalAccessCount: Int
        get() = if (!individualRecordsAvailable) 0 else
            events.filter { it.event.isAggregated }.sumOf { it.event.accessCount }

    val rejectCount: Int
        get() = events.sumOf { it.event.rejectCount }

    val appCount: Int
        get() = events.distinctBy { it.app.packageName }.size

    /** An interval holding only denied attempts ends near now, which is no access. */
    val latestAccessTimeMillis: Long?
        get() = events.filter { it.event.accessCount > 0 }.maxOfOrNull { it.event.accessTimeMillis }
}

data class ResolvedHistoryEvent(
    val event: AppOpHistoryEvent,
    val app: InstalledApp,
)
