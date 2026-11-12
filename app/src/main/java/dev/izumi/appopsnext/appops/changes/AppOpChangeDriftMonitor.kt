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
 *
 * A setting found changed elsewhere for the first time is read once more
 * between writes and then flagged in the log, so it is not marked as this
 * app's change again when its mode comes back to the recorded one. Nothing
 * is flagged until [quietAfterWriteMillis] have passed since this app last
 * wrote, see [scan].
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
    /** Flags records in the log, see [AppOpChangeLog.markChangedElsewhere]. */
    private val markChangedElsewhere: suspend (Collection<AppOpChangeRecord>) -> Unit = {},
    /**
     * Runs [action] while no write of this app is in progress, with the time
     * since the last one ended, null when there was none.
     */
    private val betweenWrites: suspend (action: suspend (sinceLastWriteMillis: Long?) -> Unit) -> Unit =
        { it(null) },
    scope: CoroutineScope,
    private val settleMillis: Long = SETTLE_MILLIS,
    private val quietAfterWriteMillis: Long = QUIET_AFTER_WRITE_MILLIS,
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
                scan(current)
            }
        }
    }

    /**
     * Reads again, for a return to the app after settings may have changed,
     * or for a page that shows a setting changed elsewhere the log has not
     * flagged yet.
     */
    fun refresh() {
        refreshes.update { it + 1 }
    }

    private suspend fun scan(records: List<AppOpChangeRecord>) {
        if (records.isEmpty()) {
            mutableDrifts.value = emptyList()
            return
        }
        val appsByUser = records.map(AppOpChangeRecord::userId).distinct().mapNotNull { userId ->
            attempt { loadApps(userId) }?.let { apps -> userId to apps.associateBy(InstalledApp::packageName) }
        }.toMap()
        val states = read(records, appsByUser)
        mutableDrifts.value = AppOpChangeDrifts.find(records, appsByUser, states)
        val candidates = AppOpChangeDrifts.newlyChanged(records, appsByUser, states)
        if (candidates.isEmpty()) return
        // A write of this app sets the mode before its record reaches the log,
        // and a failed write puts the old mode back. Read again once no write
        // is running, so neither is taken for a change made elsewhere. A record
        // a write replaced in the meantime no longer matches and stays as it is.
        //
        // Android can also move a mode on its own after a write has ended, as
        // when it derives a runtime-permission operation from the grant again
        // after rejecting a write. A mode read in that time is this app's
        // doing, not a change elsewhere, so nothing is flagged until the writes
        // have been quiet for a while, and the settings are read again then.
        var waitMillis = 0L
        attempt {
            betweenWrites { sinceLastWriteMillis ->
                if (sinceLastWriteMillis != null && sinceLastWriteMillis < quietAfterWriteMillis) {
                    waitMillis = quietAfterWriteMillis - sinceLastWriteMillis
                    return@betweenWrites
                }
                val confirmed = AppOpChangeDrifts.newlyChanged(
                    candidates, appsByUser, read(candidates, appsByUser),
                )
                if (confirmed.isNotEmpty()) markChangedElsewhere(confirmed)
            }
        }
        if (waitMillis > 0) {
            delay(waitMillis)
            refresh()
        }
    }

    private suspend fun read(
        records: List<AppOpChangeRecord>,
        appsByUser: Map<Int, Map<String, InstalledApp>>,
    ): Map<AppOpChangeRead, OpStates> =
        AppOpChangeDrifts.reads(records, appsByUser).mapNotNull { (read, uids) ->
            attempt {
                loadOpStates(AppOpNames.shellName(read.stableOperationName), read.userId, uids)
            }?.let { read to it }
        }.toMap()

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

        /** Longer than the ten seconds a mode has been seen to take to settle after a write. */
        const val QUIET_AFTER_WRITE_MILLIS = 15_000L
    }
}
