package dev.izumi.appopsnext.appops.changes

import dev.izumi.appopsnext.appops.model.AppOpNames
import dev.izumi.appopsnext.appops.parser.OpStates
import dev.izumi.appopsnext.apps.model.InstalledApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Keeps the list of settings changed elsewhere up to date while the app is
 * open, so they are seen without opening each app.
 *
 * It reads again when the change log changes, when the backend connects and
 * on [refresh]. Without a backend the last list stays as it was.
 */
class AppOpChangeDriftMonitor(
    records: Flow<List<AppOpChangeRecord>>,
    connected: Flow<Boolean>,
    /** The installed apps of one user. May throw for a user that is gone. */
    private val loadApps: suspend (userId: Int) -> List<InstalledApp>,
    private val loadOpStates: suspend (
        operationName: String,
        userId: Int,
        uids: Collection<Int>,
    ) -> OpStates,
    scope: CoroutineScope,
    private val settleMillis: Long = SETTLE_MILLIS,
) {
    private val refreshes = MutableStateFlow(0)
    private val mutableDrifts = MutableStateFlow<List<AppOpChangeDrift>>(emptyList())

    /** The latest change first. */
    val drifts: StateFlow<List<AppOpChangeDrift>> = mutableDrifts.asStateFlow()

    init {
        scope.launch {
            combine(records, connected, refreshes) { current, isConnected, _ ->
                current.takeIf { isConnected }
            }.collectLatest { current ->
                current ?: return@collectLatest
                // A batch records one change after another. Read once it is quiet.
                delay(settleMillis)
                mutableDrifts.value = scan(current)
            }
        }
    }

    /** Reads again, for a return to the app after settings may have changed. */
    fun refresh() {
        refreshes.update { it + 1 }
    }

    private suspend fun scan(records: List<AppOpChangeRecord>): List<AppOpChangeDrift> {
        if (records.isEmpty()) return emptyList()
        val appsByUser = records.map(AppOpChangeRecord::userId).distinct().mapNotNull { userId ->
            attempt { loadApps(userId) }?.let { apps -> userId to apps.associateBy(InstalledApp::packageName) }
        }.toMap()
        val states = AppOpChangeDrifts.reads(records, appsByUser).mapNotNull { (read, uids) ->
            attempt {
                loadOpStates(AppOpNames.shellName(read.stableOperationName), read.userId, uids)
            }?.let { read to it }
        }.toMap()
        return AppOpChangeDrifts.find(records, appsByUser, states)
    }

    /** A failed read leaves its settings out of this pass. The next pass reads again. */
    private inline fun <T> attempt(action: () -> T): T? =
        try {
            action()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }

    private companion object {
        const val SETTLE_MILLIS = 400L
    }
}
