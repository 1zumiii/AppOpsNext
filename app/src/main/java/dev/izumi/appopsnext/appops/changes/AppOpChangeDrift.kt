package dev.izumi.appopsnext.appops.changes

import dev.izumi.appopsnext.appops.parser.OpStates
import dev.izumi.appopsnext.apps.model.InstalledApp

/** A setting this app changed that something else has changed since. */
data class AppOpChangeDrift(
    val app: InstalledApp,
    val status: AppOpChangeStatus.ChangedElsewhere,
)

/** One operation of one user, the unit a mode read covers. */
data class AppOpChangeRead(
    val userId: Int,
    val stableOperationName: String,
)

/**
 * Compares the change log with the device. Only settings in the log are
 * looked at, so the reads are the operations the log names and nothing else.
 */
object AppOpChangeDrifts {
    /**
     * The reads the records need, each with the UIDs of the apps it concerns.
     * [appsByUser] holds the installed apps of each user by package name. A
     * record whose app is gone, or was installed again since, needs no read.
     */
    fun reads(
        records: List<AppOpChangeRecord>,
        appsByUser: Map<Int, Map<String, InstalledApp>>,
    ): Map<AppOpChangeRead, Set<Int>> {
        val uids = linkedMapOf<AppOpChangeRead, MutableSet<Int>>()
        current(records, appsByUser).forEach { (app, record) ->
            uids.getOrPut(AppOpChangeRead(record.userId, record.stableOperationName), ::mutableSetOf)
                .add(app.uid)
        }
        return uids
    }

    /**
     * The settings changed elsewhere, the latest change first. A read missing
     * from [states] failed, and its settings are left out rather than guessed.
     */
    fun find(
        records: List<AppOpChangeRecord>,
        appsByUser: Map<Int, Map<String, InstalledApp>>,
        states: Map<AppOpChangeRead, OpStates>,
    ): List<AppOpChangeDrift> =
        current(records, appsByUser)
            .groupBy({ (app, record) -> app to AppOpChangeRead(record.userId, record.stableOperationName) }) {
                it.second
            }
            .mapNotNull { (key, settingRecords) ->
                val (app, read) = key
                val opStates = states[read] ?: return@mapNotNull null
                val status = AppOpChangeRecords.status(
                    packageRecords = settingRecords,
                    operationName = read.stableOperationName,
                ) { scope -> opStates.modeAt(app.uid, app.packageName, scope) }
                (status as? AppOpChangeStatus.ChangedElsewhere)?.let { AppOpChangeDrift(app, it) }
            }
            .sortedByDescending { it.status.record.timeMillis }

    private fun current(
        records: List<AppOpChangeRecord>,
        appsByUser: Map<Int, Map<String, InstalledApp>>,
    ): List<Pair<InstalledApp, AppOpChangeRecord>> =
        records.mapNotNull { record ->
            val app = appsByUser[record.userId]?.get(record.packageName) ?: return@mapNotNull null
            if (AppOpChangeRecords.isCurrent(record, app.firstInstallTimeMillis)) app to record else null
        }
}
