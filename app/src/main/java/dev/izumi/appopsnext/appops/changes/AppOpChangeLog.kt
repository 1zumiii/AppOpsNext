package dev.izumi.appopsnext.appops.changes

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.izumi.appopsnext.appops.model.AppOpNames
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The settings of other apps on this device, so it stays out of backups like
 * the guard state.
 */
private val Context.appOpChangesDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "app_op_changes")

/**
 * The settings this app changed and the mode each change left.
 *
 * Every edit rewrites the whole log, so edits go through one queue and the
 * edits waiting in it share a single write, in the order they were made.
 */
class AppOpChangeLog(
    context: Context,
    /** The user this app runs in, for writes that name no user. */
    private val currentUserId: Int,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val store = context.appOpChangesDataStore
    private val pending = Channel<Edit>(Channel.UNLIMITED)

    init {
        // Lives as long as the process, like the application that holds this log.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { writeQueued() }
    }

    /** Newest first. */
    val records: Flow<List<AppOpChangeRecord>> = store.data.map(::decode).flowOn(Dispatchers.Default)

    /**
     * Queues the change and returns without waiting for the write, so a batch
     * of changes costs one write. The queue never refuses or drops a change.
     */
    suspend fun record(change: AppOpModeChange) {
        pending.trySend(Edit.Record(change, clock()))
    }

    /** Keeps the mode on the device as it is and stops marking it. */
    suspend fun forget(userId: Int?, packageName: String, operationName: String) {
        val user = userId ?: currentUserId
        val stableName = AppOpNames.stableName(operationName)
        update { records ->
            records.filterNot {
                it.userId == user && it.packageName == packageName &&
                    it.stableOperationName == stableName
            }
        }
    }

    /**
     * The one place that flags settings as changed elsewhere, see
     * [AppOpChangeRecords.markChangedElsewhere]. It runs after the changes
     * queued before it, so a write this app made since keeps its new record.
     */
    suspend fun markChangedElsewhere(seen: Collection<AppOpChangeRecord>) {
        if (seen.isEmpty()) return
        update { AppOpChangeRecords.markChangedElsewhere(it, seen) }
    }

    /** See [AppOpChangeRecords.prune]. */
    suspend fun prune(userId: Int?, installed: Map<String, Long?>, keepRemovedMillis: Long? = null) {
        update {
            AppOpChangeRecords.prune(it, userId ?: currentUserId, installed, keepRemovedMillis, clock())
        }
    }

    /** See [AppOpChangeRecords.restorable], for the user this app runs in. */
    suspend fun restorable(packageName: String, firstInstallTimeMillis: Long): List<AppOpChangeRecord> =
        AppOpChangeRecords.restorable(records.first(), currentUserId, packageName, firstInstallTimeMillis)

    /** Runs after the changes queued before it, and waits for its write. */
    private suspend fun update(transform: (List<AppOpChangeRecord>) -> List<AppOpChangeRecord>) {
        val edit = Edit.Update(transform)
        pending.trySend(edit)
        edit.done.await()
    }

    private suspend fun writeQueued() {
        for (first in pending) {
            val batch = mutableListOf(first)
            while (true) batch += pending.tryReceive().getOrNull() ?: break
            val failure = try {
                store.edit { preferences -> apply(batch, preferences) }
                null
            } catch (error: Exception) {
                // Changes in a failed write stay unmarked, as a failed record always left them.
                error
            }
            batch.forEach { edit ->
                if (edit is Edit.Update) {
                    if (failure == null) edit.done.complete(Unit) else edit.done.completeExceptionally(failure)
                }
            }
        }
    }

    private fun apply(batch: List<Edit>, preferences: MutablePreferences) {
        var records = decode(preferences)
        var changed = false
        batch.forEach { edit ->
            val updated = when (edit) {
                is Edit.Record -> AppOpChangeRecords.merge(
                    records = records,
                    change = edit.change,
                    userId = edit.change.userId ?: currentUserId,
                    nowMillis = edit.timeMillis,
                )
                is Edit.Update -> edit.transform(records).takeIf { it != records } ?: records
            }
            if (updated !== records) changed = true
            records = updated
        }
        if (changed) preferences[RECORDS] = records.mapTo(mutableSetOf(), AppOpChangeCodec::encode)
    }

    private fun decode(preferences: Preferences): List<AppOpChangeRecord> =
        preferences[RECORDS].orEmpty()
            .mapNotNull(AppOpChangeCodec::decode)
            .sortedByDescending(AppOpChangeRecord::timeMillis)

    private sealed interface Edit {
        class Record(val change: AppOpModeChange, val timeMillis: Long) : Edit

        class Update(val transform: (List<AppOpChangeRecord>) -> List<AppOpChangeRecord>) : Edit {
            val done = CompletableDeferred<Unit>()
        }
    }

    private companion object {
        val RECORDS = stringSetPreferencesKey("records")
    }
}
