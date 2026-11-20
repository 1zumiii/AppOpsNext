package dev.izumi.appopsnext.presentation.settings

import dev.izumi.appopsnext.history.HistoryArchiveProblem
import dev.izumi.appopsnext.history.HistoryArchiveRecorder
import dev.izumi.appopsnext.history.HistoryArchiveStore
import dev.izumi.appopsnext.settings.AppLanguage
import dev.izumi.appopsnext.settings.AppTheme
import dev.izumi.appopsnext.update.UpdateState
import dev.izumi.appopsnext.settings.UserSettingsDefaults

data class SettingsUiState(
    val hideSystemApps: Boolean = UserSettingsDefaults.HIDE_SYSTEM_APPS,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val appTheme: AppTheme = AppTheme.SYSTEM,
    val updateState: UpdateState = UpdateState.Idle,
    val ignoredUpdateVersion: String? = null,
    val seenUpdateVersion: String? = null,
    val saveIndividualHistory: Boolean = UserSettingsDefaults.SAVE_INDIVIDUAL_HISTORY,
    /** The operations chosen for saving, among [HistoryArchiveRecorder.SAVEABLE_OPERATIONS]. */
    val savedHistoryOperations: Set<String> = emptySet(),
    val savedHistory: SavedHistorySummary = SavedHistorySummary(),
    val backup: BackupUiState = BackupUiState.Idle,
) {
    /**
     * The available version, unless the user ignored it. The notifications card
     * and the dots in Settings follow this, whether or not it was seen. The
     * version row inside About still names [updateState] as it is.
     */
    val updateNotice: UpdateState.Available?
        get() = (updateState as? UpdateState.Available)
            ?.takeIf { it.versionName != ignoredUpdateVersion }

    /** Whether the bell badge counts [updateNotice]: only until the notifications page has shown it. */
    val updateUnseen: Boolean
        get() = updateNotice?.let { it.versionName != seenUpdateVersion } == true
}

data class SavedHistorySummary(
    val recordCount: Int = 0,
    val oldestMillis: Long? = null,
    val newestMillis: Long? = null,
    /** Past this many records the oldest are removed. */
    val capacity: Int = HistoryArchiveStore.MAX_EVENTS,
    val problem: HistoryArchiveProblem? = null,
)
