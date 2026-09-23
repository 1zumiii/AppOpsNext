package dev.izumi.appopsnext.presentation.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.izumi.appopsnext.AppOpsNextApplication
import dev.izumi.appopsnext.settings.AppLanguage
import dev.izumi.appopsnext.settings.ApplicationLanguageManager
import dev.izumi.appopsnext.update.UpdateChecker
import dev.izumi.appopsnext.update.UpdateState
import dev.izumi.appopsnext.history.ArchivedHistory
import dev.izumi.appopsnext.history.HistoryArchiveProblem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

class SettingsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository =
        getApplication<AppOpsNextApplication>().userSettingsRepository
    private val languageManager = ApplicationLanguageManager(application)
    private val appLanguage =
        MutableStateFlow(languageManager.currentLanguage())
    private val updateChecker = UpdateChecker()
    private val updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    private val archiveStore =
        getApplication<AppOpsNextApplication>().historyArchiveStore
    /** Summarized off the main thread: the archive can hold a hundred thousand records. */
    private val savedHistory = combine(archiveStore.contents, archiveStore.problem) { archive, problem ->
        summarize(archive.orEmpty(), problem)
    }.flowOn(Dispatchers.Default)
    val uiState = combine(
        repository.settings,
        appLanguage,
        updateState,
        savedHistory,
    ) { settings, language, update, saved ->
            SettingsUiState(
                hideSystemApps = settings.hideSystemApps,
                appLanguage = language,
                updateState = update,
                saveIndividualHistory = settings.saveIndividualHistory,
                savedHistoryOperations = settings.savedHistoryOperations.orEmpty(),
                savedHistory = saved,
            )
    }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsUiState(),
        )

    init {
        checkForUpdate()
        viewModelScope.launch { archiveStore.load() }
    }

    /**
     * Runs once when the activity is created and on demand from the version row.
     * The result is never surfaced as a notification or a dialog.
     */
    fun checkForUpdate() {
        if (updateState.value == UpdateState.Checking) return
        viewModelScope.launch {
            updateState.value = UpdateState.Checking
            updateState.value = updateChecker.check()
        }
    }

    fun setHideSystemApps(hidden: Boolean) {
        viewModelScope.launch {
            repository.setHideSystemApps(hidden)
        }
    }

    fun setSaveIndividualHistory(enabled: Boolean) {
        viewModelScope.launch {
            repository.setSaveIndividualHistory(enabled)
        }
    }

    fun setHistoryOperationSaved(operation: String, saved: Boolean) {
        viewModelScope.launch {
            repository.setHistoryOperationSaved(operation, saved)
        }
    }

    /** Counts what [deleteSavedHistory] would remove, for its confirmation. */
    fun countSavedHistory(range: SavedHistoryDateRange?): Int =
        if (range == null) {
            uiState.value.savedHistory.recordCount
        } else {
            val (from, to) = range.millis(ZoneId.systemDefault())
            archiveStore.countBetween(from, to)
        }

    /** Null deletes everything. */
    fun deleteSavedHistory(range: SavedHistoryDateRange?) {
        viewModelScope.launch {
            if (range == null) {
                archiveStore.deleteAll()
            } else {
                val (from, to) = range.millis(ZoneId.systemDefault())
                archiveStore.deleteBetween(from, to)
            }
        }
    }

    fun setAppLanguage(language: AppLanguage) {
        if (appLanguage.value == language) return
        appLanguage.value = language
        languageManager.setLanguage(language)
    }

    private fun summarize(
        archive: Map<String, ArchivedHistory>,
        problem: HistoryArchiveProblem?,
    ): SavedHistorySummary {
        var count = 0
        var oldest = Long.MAX_VALUE
        var newest = Long.MIN_VALUE
        archive.values.forEach { archived ->
            archived.events.forEach {
                count++
                oldest = minOf(oldest, it.event.accessTimeMillis)
                newest = maxOf(newest, it.event.accessTimeMillis)
            }
        }
        return SavedHistorySummary(
            recordCount = count,
            oldestMillis = oldest.takeIf { count > 0 },
            newestMillis = newest.takeIf { count > 0 },
            problem = problem,
        )
    }
}
