package dev.izumi.appopsnext.temporary

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.guard.GuardWriteTarget
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class TemporaryAllowRepository(context: Context) {
    private val store = PreferenceDataStoreFactory.create {
        File(context.noBackupFilesDir, "temporary_allow.preferences_pb")
    }

    val active: Flow<List<TemporaryAllow>> = store.data.map { preferences ->
        preferences[ENTRIES].orEmpty().mapNotNull(TemporaryAllowCodec::decode)
    }

    suspend fun entries(): List<TemporaryAllow> = active.first()

    suspend fun put(entry: TemporaryAllow) = store.edit { preferences ->
        preferences[ENTRIES] = preferences[ENTRIES].orEmpty().filterNot {
            TemporaryAllowCodec.decode(it)?.target == entry.target
        }.toSet() + TemporaryAllowCodec.encode(entry)
    }

    suspend fun remove(entry: TemporaryAllow) = store.edit { preferences ->
        preferences[ENTRIES] = preferences[ENTRIES].orEmpty().filterNot {
            TemporaryAllowCodec.decode(it) == entry
        }.toSet()
    }

    /** A permanent write in a different scope leaves the temporary slot to be restored. */
    suspend fun supersede(writes: Set<Pair<GuardWriteTarget, AppOpScope>>) = store.edit { preferences ->
        val encoded = preferences[ENTRIES] ?: return@edit
        val remaining = encoded.filterNot { value ->
            TemporaryAllowCodec.decode(value)?.let { (it.target to it.scope) in writes } == true
        }.toSet()
        if (remaining != encoded) preferences[ENTRIES] = remaining
    }

    private companion object {
        val ENTRIES = stringSetPreferencesKey("entries_v1")
    }
}

internal object TemporaryAllowCodec {
    fun encode(entry: TemporaryAllow): String = with(entry) {
        listOf(
            target.packageName, target.uid, target.stableOperationName, scope.name,
            originalMode.name, openByMillis, phase.name,
            offScreenSinceMillis ?: "",
            displayedOriginalMode.name,
            appliedMode.name,
            awaitingUidReadback,
        ).joinToString("|")
    }

    fun decode(value: String): TemporaryAllow? {
        val fields = value.split('|')
        if (fields.size !in 7..11) return null
        val originalMode = AppOpMode.entries.firstOrNull { it.name == fields[4] } ?: return null
        return TemporaryAllow(
            target = GuardWriteTarget(fields[0], fields[1].toIntOrNull() ?: return null, fields[2]),
            scope = AppOpScope.entries.firstOrNull { it.name == fields[3] } ?: return null,
            originalMode = originalMode,
            openByMillis = fields[5].toLongOrNull() ?: return null,
            phase = TemporaryAllow.Phase.entries.firstOrNull { it.name == fields[6] } ?: return null,
            offScreenSinceMillis = fields.getOrNull(7)?.toLongOrNull(),
            displayedOriginalMode = AppOpMode.entries.firstOrNull { it.name == fields.getOrNull(8) } ?: originalMode,
            appliedMode = AppOpMode.entries.firstOrNull { it.name == fields.getOrNull(9) } ?: AppOpMode.ALLOW,
            awaitingUidReadback = fields.getOrNull(10) == "true",
        )
    }
}
