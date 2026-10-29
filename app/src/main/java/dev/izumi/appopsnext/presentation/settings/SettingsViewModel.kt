package dev.izumi.appopsnext.presentation.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.izumi.appopsnext.AppOpsNextApplication
import dev.izumi.appopsnext.backup.BackupFormatException
import dev.izumi.appopsnext.backup.BackupProblem
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
import kotlinx.coroutines.withContext
import java.io.IOException
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
    private val backupService =
        getApplication<AppOpsNextApplication>().appBackupService
    private val backupState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val uiState = combine(
        repository.settings,
        appLanguage,
        updateState,
        savedHistory,
        backupState,
    ) { settings, language, update, saved, backup ->
            SettingsUiState(
                hideSystemApps = settings.hideSystemApps,
                appLanguage = language,
                updateState = update,
                saveIndividualHistory = settings.saveIndividualHistory,
                savedHistoryOperations = settings.savedHistoryOperations.orEmpty(),
                savedHistory = saved,
                backup = backup,
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

    fun exportBackup(uri: Uri) {
        if (backupState.value == BackupUiState.Working) return
        backupState.value = BackupUiState.Working
        viewModelScope.launch {
            val outcome = runCatching {
                val text = backupService.export(System.currentTimeMillis())
                withContext(Dispatchers.IO) {
                    val stream = getApplication<Application>().contentResolver
                        .openOutputStream(uri, "wt")
                        ?: throw IOException("No stream for $uri")
                    stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
            }.fold(
                onSuccess = { BackupOutcome.EXPORTED },
                onFailure = { BackupOutcome.EXPORT_FAILED },
            )
            backupState.value = BackupUiState.Finished(outcome)
        }
    }

    /** Reads and checks [uri] without writing anything, for the import preview. */
    fun openBackup(uri: Uri) {
        if (backupState.value == BackupUiState.Working) return
        backupState.value = BackupUiState.Working
        viewModelScope.launch {
            backupState.value = try {
                val text = withContext(Dispatchers.IO) { readBackupFile(uri) }
                if (text == null) {
                    BackupUiState.Rejected(BackupProblem.FOREIGN)
                } else {
                    BackupUiState.Preview(backupService.preview(text))
                }
            } catch (error: BackupFormatException) {
                BackupUiState.Rejected(error.problem)
            } catch (error: IOException) {
                BackupUiState.Rejected(null)
            } catch (error: SecurityException) {
                BackupUiState.Rejected(null)
            }
        }
    }

    fun confirmBackupImport() {
        val preview = (backupState.value as? BackupUiState.Preview)?.preview ?: return
        backupState.value = BackupUiState.Working
        viewModelScope.launch {
            val outcome = runCatching { backupService.import(preview.backup, preview.guard) }.fold(
                onSuccess = { BackupOutcome.IMPORTED },
                onFailure = { BackupOutcome.IMPORT_FAILED },
            )
            backupState.value = BackupUiState.Finished(outcome)
        }
    }

    fun dismissBackup() {
        if (backupState.value != BackupUiState.Working) {
            backupState.value = BackupUiState.Idle
        }
    }

    /** Null when the file is too large to be a backup, which also keeps a wrong pick from filling memory. */
    private fun readBackupFile(uri: Uri): String? {
        val stream = getApplication<Application>().contentResolver.openInputStream(uri)
            ?: throw IOException("No stream for $uri")
        val bytes = stream.use { it.readNBytes(MAX_BACKUP_BYTES + 1) }
        if (bytes.size > MAX_BACKUP_BYTES) return null
        return String(bytes, Charsets.UTF_8)
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

    private companion object {
        /** Far above any real backup, which holds a few kilobytes of text. */
        const val MAX_BACKUP_BYTES = 4 * 1024 * 1024
    }
}
