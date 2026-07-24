package dev.izumi.appopsnext.presentation.history

import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.history.model.AppOpHistoryEvent
import dev.izumi.appopsnext.history.model.AppOpHistoryFailureReason
import dev.izumi.appopsnext.history.model.TrackedHistoryPermission

data class HistoryUiState(
    val isLoading: Boolean = false,
    val waitingForBackend: Boolean = true,
    val permissions: List<PermissionHistory> = emptyList(),
    val failureReason: AppOpHistoryFailureReason? = null,
    val partialFailureCount: Int = 0,
)

data class PermissionHistory(
    val permission: TrackedHistoryPermission,
    val events: List<ResolvedHistoryEvent>,
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
