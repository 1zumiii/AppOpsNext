package dev.izumi.appopsnext.newapps

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.izumi.appopsnext.newapps.model.InstalledPackageFingerprint
import dev.izumi.appopsnext.newapps.model.NewAppReconcileResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

private val Context.newAppPolicyDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "new_app_policy")

class NewAppPolicyStateRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.newAppPolicyDataStore)

    /** The rule outcomes kept for recent installations, the latest installation first. */
    val results: Flow<List<NewAppResult>> = dataStore.data
        .map { preferences ->
            NewAppResult.group(
                preferences[Keys.RULE_PROGRESS].orEmpty().mapNotNull(NewAppRuleProgressCodec::decode),
            )
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    suspend fun reconcile(
        installed: List<InstalledPackageFingerprint>,
    ): NewAppReconcileResult {
        var result = NewAppReconcileResult(
            initializedBaseline = false,
            detected = emptyList(),
        )
        dataStore.edit { preferences ->
            val installedSet = installed.toSet()
            if (preferences[Keys.BASELINE_INITIALIZED] != true) {
                preferences[Keys.SEEN_INSTALLATIONS] = encode(installedSet)
                preferences[Keys.BASELINE_INITIALIZED] = true
                preferences.remove(Keys.PENDING_INSTALLATIONS)
                result = result.copy(initializedBaseline = true)
                return@edit
            }

            val seen = decode(preferences[Keys.SEEN_INSTALLATIONS])
            val detected = installedSet.subtract(seen)
            val pending = decode(preferences[Keys.PENDING_INSTALLATIONS])
            preferences[Keys.SEEN_INSTALLATIONS] = encode(installedSet)
            preferences[Keys.PENDING_INSTALLATIONS] =
                encode(pending + detected)
            result = result.copy(
                detected = detected.sortedBy(
                    InstalledPackageFingerprint::firstInstallTimeMillis,
                ),
            )
        }
        return result
    }

    suspend fun pending(): List<InstalledPackageFingerprint> =
        decode(dataStore.data.first()[Keys.PENDING_INSTALLATIONS])
            .sortedBy(InstalledPackageFingerprint::firstInstallTimeMillis)

    suspend fun progress(fingerprint: InstalledPackageFingerprint): List<NewAppRuleProgress> =
        dataStore.data.first()[Keys.RULE_PROGRESS].orEmpty()
            .mapNotNull(NewAppRuleProgressCodec::decode)
            .filter { it.installation == fingerprint }

    suspend fun recordProgress(progress: NewAppRuleProgress) {
        dataStore.edit { preferences ->
            val current = preferences[Keys.RULE_PROGRESS].orEmpty()
                .mapNotNull(NewAppRuleProgressCodec::decode)
                .filterNot {
                    it.installation == progress.installation &&
                        it.item.target.stableOperationName == progress.item.target.stableOperationName
                }
            preferences[Keys.RULE_PROGRESS] = (current + progress)
                .mapTo(mutableSetOf(), NewAppRuleProgressCodec::encode)
        }
    }

    suspend fun markProcessed(fingerprint: InstalledPackageFingerprint) {
        dataStore.edit { preferences ->
            val pending = decode(preferences[Keys.PENDING_INSTALLATIONS])
            preferences[Keys.PENDING_INSTALLATIONS] =
                encode(pending - fingerprint)
            val records = preferences[Keys.RULE_PROGRESS].orEmpty()
                .mapNotNull(NewAppRuleProgressCodec::decode)
            val retained = records.map { it.installation }.distinct()
                .sortedByDescending { it.firstInstallTimeMillis }.take(50).toSet() +
                (pending - fingerprint)
            preferences[Keys.RULE_PROGRESS] = records.filter { it.installation in retained }
                .mapTo(mutableSetOf(), NewAppRuleProgressCodec::encode)
        }
    }

    /**
     * Forgets the outcomes shown for installations that are done. Which
     * installations were seen and which are still waiting stays as it is, so no
     * app is taken for new again. An installation still waiting keeps its
     * outcomes, because the next attempt resumes from them instead of applying
     * the rules that already succeeded a second time.
     *
     * Returns the installations whose outcomes were forgotten.
     */
    suspend fun clearResults(): Set<InstalledPackageFingerprint> {
        var cleared = emptySet<InstalledPackageFingerprint>()
        dataStore.edit { preferences ->
            val pending = decode(preferences[Keys.PENDING_INSTALLATIONS])
            val (kept, dropped) = preferences[Keys.RULE_PROGRESS].orEmpty().partition { encoded ->
                NewAppRuleProgressCodec.decode(encoded)?.installation in pending
            }
            preferences[Keys.RULE_PROGRESS] = kept.toSet()
            cleared = dropped.mapNotNullTo(mutableSetOf()) { NewAppRuleProgressCodec.decode(it)?.installation }
        }
        return cleared
    }

    suspend fun reset() {
        dataStore.edit { preferences ->
            preferences.remove(Keys.BASELINE_INITIALIZED)
            preferences.remove(Keys.SEEN_INSTALLATIONS)
            preferences.remove(Keys.PENDING_INSTALLATIONS)
            preferences.remove(Keys.RULE_PROGRESS)
        }
    }

    private fun encode(
        fingerprints: Set<InstalledPackageFingerprint>,
    ): Set<String> = fingerprints.mapTo(mutableSetOf()) { fingerprint ->
        "${fingerprint.firstInstallTimeMillis}:${fingerprint.packageName}"
    }

    private fun decode(
        encoded: Set<String>?,
    ): Set<InstalledPackageFingerprint> = encoded.orEmpty().mapNotNullTo(
        mutableSetOf(),
    ) { value ->
        val separatorIndex = value.indexOf(':')
        if (separatorIndex <= 0 || separatorIndex == value.lastIndex) {
            return@mapNotNullTo null
        }
        val installTime = value.substring(0, separatorIndex).toLongOrNull()
            ?: return@mapNotNullTo null
        InstalledPackageFingerprint(
            packageName = value.substring(separatorIndex + 1),
            firstInstallTimeMillis = installTime,
        )
    }

    private object Keys {
        val RULE_PROGRESS = stringSetPreferencesKey("rule_progress_v1")
        val BASELINE_INITIALIZED =
            booleanPreferencesKey("baseline_initialized")
        val SEEN_INSTALLATIONS =
            stringSetPreferencesKey("seen_installations")
        val PENDING_INSTALLATIONS =
            stringSetPreferencesKey("pending_installations")
    }
}
