package dev.izumi.appopsnext.appops

import android.content.Context
import android.os.Build
import android.os.Process
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.izumi.appopsnext.appops.binder.HiddenApiAccess
import dev.izumi.appopsnext.apps.InstalledAppsRepository
import dev.izumi.appopsnext.diagnostics.DiagnosticLogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.appOpsCapabilityDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "app_ops_capabilities")

/** A probe outcome tied to the system build and app version it was made on. */
data class StoredReadProbe(
    val buildKey: String,
    val capabilities: Set<AppOpsReadCapability>,
    val probedAtMillis: Long,
    val notes: List<String>,
)

/** One field per line; notes come last and may be empty. */
internal object StoredReadProbeCodec {
    fun encode(probe: StoredReadProbe): String =
        listOf(
            VERSION,
            probe.buildKey.replace('\n', ' '),
            probe.capabilities.joinToString(",") { it.name },
            probe.probedAtMillis.toString(),
        ).plus(probe.notes.map { it.replace('\n', ' ') }).joinToString("\n")

    fun decode(value: String?): StoredReadProbe? {
        val lines = value?.split('\n') ?: return null
        if (lines.size < 4 || lines[0] != VERSION) return null
        val capabilities = lines[2].split(',').filter(String::isNotEmpty).map { name ->
            AppOpsReadCapability.entries.firstOrNull { it.name == name } ?: return null
        }
        return StoredReadProbe(
            buildKey = lines[1],
            capabilities = capabilities.toSet(),
            probedAtMillis = lines[3].toLongOrNull() ?: return null,
            notes = lines.drop(4),
        )
    }

    private const val VERSION = "v1"
}

/**
 * Decides which reads go through the binder on this device.
 *
 * The probe runs once per system build and app version: an OTA can change the
 * interface as easily as it changed the text format in #13. A failure at run
 * time drops the stored result so the next connection probes again.
 */
class AppOpsReadCapabilities(
    private val context: Context,
    private val structured: StructuredAppOpsReads,
    private val probe: AppOpsReadProbe,
    private val installedApps: InstalledAppsRepository,
    private val log: DiagnosticLogRepository,
) {
    enum class Source { NOT_RUN, CACHED, PROBED, FAILED }

    data class Status(
        val source: Source = Source.NOT_RUN,
        val probe: StoredReadProbe? = null,
    )

    private val dataStore = context.appOpsCapabilityDataStore
    private val mutex = Mutex()
    private val mutableStatus = MutableStateFlow(Status())
    val status: StateFlow<Status> = mutableStatus.asStateFlow()

    /** Call after the privileged backend connects; the binder reads need Shizuku up. */
    suspend fun resolve() = mutex.withLock {
        val key = buildKey()
        val stored = StoredReadProbeCodec.decode(dataStore.data.first()[PROBE_KEY])
        if (stored != null && stored.buildKey == key) {
            structured.enable(stored.capabilities)
            mutableStatus.value = Status(Source.CACHED, stored)
            return@withLock
        }
        try {
            val result = runProbe()
            val record = StoredReadProbe(key, result.capabilities, System.currentTimeMillis(), result.notes)
            dataStore.edit { it[PROBE_KEY] = StoredReadProbeCodec.encode(record) }
            structured.enable(record.capabilities)
            mutableStatus.value = Status(Source.PROBED, record)
            log.info(SOURCE, "Read probe: ${describe(record)}")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            structured.enable(emptySet())
            mutableStatus.value = Status(Source.FAILED)
            log.error(SOURCE, "Read probe failed", error)
        }
    }

    /** A binder read failed after the probe passed, so this build needs probing again. */
    suspend fun invalidate(capability: AppOpsReadCapability, error: Throwable) {
        log.warning(
            SOURCE,
            "Binder read ${capability.name} failed and falls back to text: " +
                "${error::class.java.simpleName}: ${error.message}",
        )
        dataStore.edit { it.remove(PROBE_KEY) }
    }

    private suspend fun runProbe(): AppOpsReadProbeResult {
        val apps = installedApps.loadInstalledApps().map { it.uid to it.packageName }
        val own = Process.myUid() to context.packageName
        val samples = listOf(own) + apps.filter { it.second == SETTINGS_PACKAGE }
        return probe.run(
            userId = Process.myUid() / PER_USER_RANGE,
            apps = apps,
            samples = samples,
        )
    }

    private fun buildKey(): String {
        val versionCode = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        return "${Build.FINGERPRINT}#$versionCode"
    }

    companion object {
        /** The live read paths, which drop to text after a failure even while the stored probe still says binder. */
        fun report(status: Status, enabled: Set<AppOpsReadCapability>): String {
            val paths = AppOpsReadCapability.entries.joinToString(",") { capability ->
                "${capability.name.lowercase()}=${if (capability in enabled) "binder" else "text"}"
            }
            val probe = status.probe ?: return "$paths (probe=${status.source.name.lowercase()})"
            return "$paths (probe=${status.source.name.lowercase()}, " +
                "probedAt=${probe.probedAtMillis}, hiddenApiExemption=${HiddenApiAccess.exempted}, " +
                "build=${probe.buildKey})" +
                probe.notes.takeIf { it.isNotEmpty() }?.joinToString(prefix = "\nreadProbe=", separator = ", ").orEmpty()
        }

        fun describe(probe: StoredReadProbe): String =
            AppOpsReadCapability.entries.joinToString(", ") { capability ->
                "${capability.name}=${if (capability in probe.capabilities) "binder" else "text"}"
            } + probe.notes.takeIf { it.isNotEmpty() }?.joinToString(prefix = "; ", separator = ", ").orEmpty()

        private val PROBE_KEY = stringPreferencesKey("read_probe_v1")
        private const val SOURCE = "AppOpsReads"
        private const val SETTINGS_PACKAGE = "com.android.settings"

        /** Android's `UserHandle.PER_USER_RANGE`. */
        private const val PER_USER_RANGE = 100_000
    }
}
