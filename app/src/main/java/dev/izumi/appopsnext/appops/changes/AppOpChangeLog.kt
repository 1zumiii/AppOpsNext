package dev.izumi.appopsnext.appops.changes

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.izumi.appopsnext.appops.model.AppOpNames
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The settings of other apps on this device, so it stays out of backups like
 * the guard state.
 */
private val Context.appOpChangesDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "app_op_changes")

/** The settings this app changed and the mode each change left. */
class AppOpChangeLog(
    context: Context,
    /** The user this app runs in, for writes that name no user. */
    private val currentUserId: Int,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val store = context.appOpChangesDataStore

    /** Newest first. */
    val records: Flow<List<AppOpChangeRecord>> = store.data.map(::decode)

    suspend fun record(change: AppOpModeChange) {
        store.edit { preferences ->
            val merged = AppOpChangeRecords.merge(
                records = decode(preferences),
                change = change,
                userId = change.userId ?: currentUserId,
                nowMillis = clock(),
            )
            preferences[RECORDS] = merged.mapTo(mutableSetOf(), AppOpChangeCodec::encode)
        }
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

    /** See [AppOpChangeRecords.prune]. */
    suspend fun prune(userId: Int?, installed: Map<String, Long?>) {
        update { AppOpChangeRecords.prune(it, userId ?: currentUserId, installed) }
    }

    private suspend fun update(transform: (List<AppOpChangeRecord>) -> List<AppOpChangeRecord>) {
        store.edit { preferences ->
            val current = decode(preferences)
            val updated = transform(current)
            if (updated.size != current.size) {
                preferences[RECORDS] = updated.mapTo(mutableSetOf(), AppOpChangeCodec::encode)
            }
        }
    }

    private fun decode(preferences: Preferences): List<AppOpChangeRecord> =
        preferences[RECORDS].orEmpty()
            .mapNotNull(AppOpChangeCodec::decode)
            .sortedByDescending(AppOpChangeRecord::timeMillis)

    private companion object {
        val RECORDS = stringSetPreferencesKey("records")
    }
}
