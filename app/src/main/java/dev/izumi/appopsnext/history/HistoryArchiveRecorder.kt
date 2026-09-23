package dev.izumi.appopsnext.history

import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.history.model.AppOpHistoryLoadResult
import dev.izumi.appopsnext.presentation.history.HistoryEventResolver
import dev.izumi.appopsnext.settings.UserSettings
import dev.izumi.appopsnext.settings.UserSettingsRepository
import dev.izumi.appopsnext.shizuku.model.PrivilegedServiceState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Saves the individual records of the operations chosen for it, whether or not
 * the history page shows them.
 *
 * The history page saves what it reads as it refreshes. Everything else is read
 * here when the app comes to the foreground, at most every six hours: the system
 * keeps seven days, so any visit within a week leaves no gap, without a command
 * the size of a whole history dump every few minutes.
 */
class HistoryArchiveRecorder(
    private val scope: CoroutineScope,
    private val settingsRepository: UserSettingsRepository,
    private val panelRepository: HistoryPermissionSettingsRepository,
    private val historyRepository: AppOpsHistoryRepository,
    private val loadInstalledApps: suspend () -> List<InstalledApp>,
    private val snapshotStore: HistorySnapshotStore,
    private val archiveStore: HistoryArchiveStore,
    private val privilegedState: StateFlow<PrivilegedServiceState>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            settingsRepository.initializeSavedHistoryOperations(savedOperations(settingsRepository.settings.first()))
            // Turning saving on, or choosing another operation, saves at once rather
            // than at the next visit. The first value is the stored one, not a change.
            var previous: Pair<Boolean, Set<String>>? = null
            settingsRepository.settings
                .map { it.saveIndividualHistory to it.savedHistoryOperations.orEmpty() }
                .distinctUntilChanged()
                .collect { current ->
                    val before = previous
                    previous = current
                    val (enabled, operations) = current
                    if (before == null || !enabled) return@collect
                    val added = if (before.first) operations - before.second else operations
                    if (added.isNotEmpty()) save(added, force = true)
                }
        }
        scope.launch {
            privilegedState.filterIsInstance<PrivilegedServiceState.Connected>().collect { saveDue() }
        }
    }

    fun onAppForeground() {
        scope.launch { saveDue() }
    }

    /**
     * Coverage is only claimed for an operation the system keeps individual
     * records for; claiming it for one that has none would hide the interval
     * counts that are its only history.
     */
    suspend fun record(operation: String, snapshot: HistorySnapshot) {
        val settings = settingsRepository.settings.first()
        if (!settings.saveIndividualHistory || operation !in savedOperations(settings)) return
        if (snapshot.events.none { !it.event.isAggregated }) return
        archiveStore.record(
            operation,
            snapshot.events,
            coveredFrom = snapshot.fetchedAtMillis - INDIVIDUAL_RECORD_RETENTION_MILLIS,
            coveredTo = snapshot.fetchedAtMillis,
        )
    }

    private suspend fun saveDue() {
        save(savedOperations(settingsRepository.settings.first()), force = false)
    }

    /**
     * Until a choice is stored, the history page's selection stands in for it, so
     * nothing it shows goes unsaved in the moment before the first choice is written.
     */
    private suspend fun savedOperations(settings: UserSettings): Set<String> =
        settings.savedHistoryOperations ?: panelRepository.selectedPermissions.first()
            .map { it.shellOperationName }
            .filterTo(mutableSetOf()) { it in SAVEABLE_OPERATIONS }

    private suspend fun save(operations: Set<String>, force: Boolean) = mutex.withLock {
        if (!settingsRepository.settings.first().saveIndividualHistory || operations.isEmpty()) return@withLock
        try {
            val stored = snapshotStore.read()
            // What was read before is saved at once, even while the backend is away.
            if (force) operations.forEach { operation -> stored[operation]?.let { record(operation, it) } }
            if (privilegedState.value !is PrivilegedServiceState.Connected) return@withLock
            val now = clock()
            val due = operations.filter { operation ->
                force || (stored[operation]?.fetchedAtMillis ?: Long.MIN_VALUE) < now - SAVE_INTERVAL_MILLIS
            }
            if (due.isEmpty()) return@withLock
            val apps = try {
                loadInstalledApps()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                return@withLock
            }
            for (operation in due) {
                val result = historyRepository.loadOperationHistory(operation)
                    as? AppOpHistoryLoadResult.Success ?: continue
                val resolved = withContext(Dispatchers.Default) {
                    HistoryEventResolver.resolve(result.events, apps, hideSystemApps = false)
                }
                val snapshot = HistorySnapshot(resolved, clock())
                snapshotStore.stage(operation, snapshot)
                record(operation, snapshot)
            }
        } finally {
            withContext(NonCancellable) {
                snapshotStore.flush()
                archiveStore.flush()
            }
        }
    }

    companion object {
        /**
         * The operations with individual records on the reference device, whose
         * system configuration is unmodified. Others only have interval counts, so
         * saving them would store nothing.
         */
        val SAVEABLE_OPERATIONS = listOf("CAMERA", "RECORD_AUDIO", "FINE_LOCATION", "COARSE_LOCATION")

        private const val SAVE_INTERVAL_MILLIS = 6 * 60 * 60 * 1000L
        private const val INDIVIDUAL_RECORD_RETENTION_MILLIS =
            AppOpsHistoryRepository.INDIVIDUAL_RECORD_RETENTION_DAYS * 24L * 60 * 60 * 1000
    }
}
