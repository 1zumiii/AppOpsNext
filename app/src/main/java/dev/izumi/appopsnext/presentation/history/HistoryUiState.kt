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
)

data class PermissionHistory(
    val permission: HistoryPermission,
    val events: List<ResolvedHistoryEvent>,
    val failureReason: AppOpHistoryFailureReason? = null,
) {
    val appCount: Int
        get() = events.distinctBy { it.app.packageName }.size

    val latestAccessTimeMillis: Long?
        get() = events.maxOfOrNull { it.event.accessTimeMillis }
}

data class ResolvedHistoryEvent(
    val event: AppOpHistoryEvent,
    val app: InstalledApp,
)
