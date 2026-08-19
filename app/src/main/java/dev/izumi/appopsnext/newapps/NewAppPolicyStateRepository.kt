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
import kotlinx.coroutines.flow.first

private val Context.newAppPolicyDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "new_app_policy")

class NewAppPolicyStateRepository(
    context: Context,
) {
    private val dataStore = context.newAppPolicyDataStore

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

    suspend fun markProcessed(fingerprint: InstalledPackageFingerprint) {
        dataStore.edit { preferences ->
            val pending = decode(preferences[Keys.PENDING_INSTALLATIONS])
            preferences[Keys.PENDING_INSTALLATIONS] =
                encode(pending - fingerprint)
        }
    }

    suspend fun reset() {
        dataStore.edit { preferences ->
            preferences.remove(Keys.BASELINE_INITIALIZED)
            preferences.remove(Keys.SEEN_INSTALLATIONS)
            preferences.remove(Keys.PENDING_INSTALLATIONS)
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
        val BASELINE_INITIALIZED =
            booleanPreferencesKey("baseline_initialized")
        val SEEN_INSTALLATIONS =
            stringSetPreferencesKey("seen_installations")
        val PENDING_INSTALLATIONS =
            stringSetPreferencesKey("pending_installations")
    }
}
