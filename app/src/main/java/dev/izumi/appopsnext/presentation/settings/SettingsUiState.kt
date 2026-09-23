package dev.izumi.appopsnext.presentation.settings

import dev.izumi.appopsnext.history.HistoryArchiveProblem
import dev.izumi.appopsnext.history.HistoryArchiveRecorder
import dev.izumi.appopsnext.history.HistoryArchiveStore
import dev.izumi.appopsnext.settings.AppLanguage
import dev.izumi.appopsnext.update.UpdateState
import dev.izumi.appopsnext.settings.UserSettingsDefaults

data class SettingsUiState(
    val hideSystemApps: Boolean = UserSettingsDefaults.HIDE_SYSTEM_APPS,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val updateState: UpdateState = UpdateState.Idle,
    val saveIndividualHistory: Boolean = UserSettingsDefaults.SAVE_INDIVIDUAL_HISTORY,
    /** The operations chosen for saving, among [HistoryArchiveRecorder.SAVEABLE_OPERATIONS]. */
    val savedHistoryOperations: Set<String> = emptySet(),
    val savedHistory: SavedHistorySummary = SavedHistorySummary(),
)

data class SavedHistorySummary(
    val recordCount: Int = 0,
    val oldestMillis: Long? = null,
    val newestMillis: Long? = null,
    /** Past this many records the oldest are removed. */
    val capacity: Int = HistoryArchiveStore.MAX_EVENTS,
    val problem: HistoryArchiveProblem? = null,
)
