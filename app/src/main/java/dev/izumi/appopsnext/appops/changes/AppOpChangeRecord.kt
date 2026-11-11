package dev.izumi.appopsnext.appops.changes

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpIdentifier
import dev.izumi.appopsnext.appops.model.AppOpNames
import dev.izumi.appopsnext.appops.model.AppOpScope

/**
 * A write this app made and verified. [userId] null means the user this app
 * runs in; the change log resolves it before storing.
 */
data class AppOpModeChange(
    val packageName: String,
    val userId: Int?,
    val operation: AppOpIdentifier,
    val scope: AppOpScope,
    val originalMode: AppOpMode,
    val appliedMode: AppOpMode,
)

/**
 * One setting this app changed. [originalMode] is the mode before the first
 * change, [recordedMode] the mode the last change left, as read back.
 */
data class AppOpChangeRecord(
    val userId: Int,
    val packageName: String,
    val stableOperationName: String,
    val scope: AppOpScope,
    val originalMode: AppOpMode,
    val recordedMode: AppOpMode,
    val timeMillis: Long,
    /**
     * When the package was first found gone, or installed again since the
     * change. Null while the record applies to the installed package.
     */
    val removedAtMillis: Long? = null,
) {
    val key: Key get() = Key(userId, packageName, stableOperationName, scope)

    data class Key(
        val userId: Int,
        val packageName: String,
        val stableOperationName: String,
        val scope: AppOpScope,
    )
}

/** What a setting looks like against the change this app last made to it. */
sealed interface AppOpChangeStatus {
    val record: AppOpChangeRecord

    /** The mode is still the one this app set. */
    data class Modified(override val record: AppOpChangeRecord) : AppOpChangeStatus

    /**
     * Something outside this app changed the mode since. Who did is unknown.
     * [currentMode] is null when the mode on the device has no known name.
     */
    data class ChangedElsewhere(
        override val record: AppOpChangeRecord,
        val currentMode: AppOpMode?,
    ) : AppOpChangeStatus
}

object AppOpChangeRecords {
    /** Older records go first once the log grows past this. */
    const val MAX_RECORDS = 4_000

    /**
     * Folds a verified write into [records]. A setting keeps the mode it had
     * before this app first changed it, and a change back to that mode removes
     * the record, since the setting is as it was.
     */
    fun merge(
        records: List<AppOpChangeRecord>,
        change: AppOpModeChange,
        userId: Int,
        nowMillis: Long,
    ): List<AppOpChangeRecord> {
        if (change.originalMode == change.appliedMode) return records
        val key = AppOpChangeRecord.Key(
            userId = userId,
            packageName = change.packageName,
            stableOperationName = AppOpNames.stableName(change.operation.shellName),
            scope = change.scope,
        )
        val existing = records.firstOrNull { it.key == key }
        val others = records.filter { it.key != key }
        // A record of an earlier installation says nothing about this one.
        val originalMode = existing?.takeIf { it.removedAtMillis == null }?.originalMode
            ?: change.originalMode
        if (change.appliedMode == originalMode) return others
        val updated = AppOpChangeRecord(
            userId = key.userId,
            packageName = key.packageName,
            stableOperationName = key.stableOperationName,
            scope = key.scope,
            originalMode = originalMode,
            recordedMode = change.appliedMode,
            timeMillis = nowMillis,
        )
        return (others + updated)
            .sortedByDescending(AppOpChangeRecord::timeMillis)
            .take(MAX_RECORDS)
    }

    /**
     * A package installed after the change is a new installation, whose
     * settings Android started over. An unknown install time keeps the record.
     */
    fun isCurrent(record: AppOpChangeRecord, firstInstallTimeMillis: Long?): Boolean =
        firstInstallTimeMillis == null || firstInstallTimeMillis <= record.timeMillis

    /**
     * [records] grouped by user and package, keeping their order, so a list of
     * apps looks each one up instead of scanning every record per app.
     */
    fun byPackage(records: List<AppOpChangeRecord>): Map<Pair<Int, String>, List<AppOpChangeRecord>> =
        records.groupBy { it.userId to it.packageName }

    /** The records of one package that still apply to its installation. */
    fun forPackage(
        records: List<AppOpChangeRecord>,
        userId: Int,
        packageName: String,
        firstInstallTimeMillis: Long?,
    ): List<AppOpChangeRecord> = records.filter {
        it.userId == userId &&
            it.packageName == packageName &&
            isCurrent(it, firstInstallTimeMillis)
    }

    /** The newest change to any operation of a package, for sorting. */
    fun latestTime(
        records: List<AppOpChangeRecord>,
        userId: Int,
        packageName: String,
        firstInstallTimeMillis: Long?,
        operationName: String? = null,
    ): Long? {
        val stableName = operationName?.let(AppOpNames::stableName)
        return forPackage(records, userId, packageName, firstInstallTimeMillis)
            .filter { stableName == null || it.stableOperationName == stableName }
            .maxOfOrNull(AppOpChangeRecord::timeMillis)
    }

    /**
     * Compares the records of one operation with the modes on the device.
     * [currentMode] gives the mode at a scope, [AppOpMode.DEFAULT] where none
     * is stored and null where the stored mode has no known name. A change
     * elsewhere at any scope outweighs the scopes still as this app left them.
     */
    fun status(
        packageRecords: List<AppOpChangeRecord>,
        operationName: String,
        currentMode: (AppOpScope) -> AppOpMode?,
    ): AppOpChangeStatus? {
        val stableName = AppOpNames.stableName(operationName)
        val records = packageRecords
            .filter { it.stableOperationName == stableName }
            .sortedByDescending(AppOpChangeRecord::timeMillis)
        if (records.isEmpty()) return null
        records.forEach { record ->
            val current = currentMode(record.scope)
            if (current != record.recordedMode) {
                return AppOpChangeStatus.ChangedElsewhere(record, current)
            }
        }
        return AppOpChangeStatus.Modified(records.first())
    }

    /** How long the settings of a removed package are kept for its next installation. */
    const val KEEP_REMOVED_MILLIS = 30L * 24 * 60 * 60 * 1000

    /**
     * Drops the records of [userId] for packages no longer installed, or
     * installed again after the change. [installed] maps each installed package
     * to its install time, null where unknown.
     *
     * With [keepRemovedMillis] such a record is kept that long from the first
     * time it was found here, so the settings can be restored when the package
     * is installed again. Without it the record is dropped at once.
     */
    fun prune(
        records: List<AppOpChangeRecord>,
        userId: Int,
        installed: Map<String, Long?>,
        keepRemovedMillis: Long? = null,
        nowMillis: Long = 0,
    ): List<AppOpChangeRecord> = records.mapNotNull { record ->
        val applies = record.userId != userId ||
            (record.packageName in installed && isCurrent(record, installed[record.packageName]))
        when {
            applies -> if (record.userId == userId && record.removedAtMillis != null) {
                record.copy(removedAtMillis = null)
            } else {
                record
            }
            keepRemovedMillis == null -> null
            record.removedAtMillis == null -> record.copy(removedAtMillis = nowMillis)
            // A clock set back must not keep a record forever.
            nowMillis - record.removedAtMillis in 0..keepRemovedMillis -> record
            else -> null
        }
    }

    /**
     * The settings to give a package installed at [firstInstallTimeMillis]
     * again: the records of its earlier installation, one per operation. A
     * record at package scope outweighs one at UID scope, since the UID is new.
     */
    fun restorable(
        records: List<AppOpChangeRecord>,
        userId: Int,
        packageName: String,
        firstInstallTimeMillis: Long,
    ): List<AppOpChangeRecord> = records
        .filter {
            it.userId == userId && it.packageName == packageName &&
                !isCurrent(it, firstInstallTimeMillis)
        }
        .groupBy(AppOpChangeRecord::stableOperationName)
        .map { (_, sameOperation) ->
            sameOperation.sortedWith(
                compareBy<AppOpChangeRecord> { it.scope != AppOpScope.PACKAGE }
                    .thenByDescending(AppOpChangeRecord::timeMillis),
            ).first()
        }
        .sortedBy(AppOpChangeRecord::stableOperationName)
}

/**
 * Tab-separated records, one per string, like the guard codec. A record that
 * does not parse is dropped rather than failing the whole log.
 */
internal object AppOpChangeCodec {
    fun encode(record: AppOpChangeRecord): String = listOfNotNull(
        V1, record.userId, record.packageName, record.stableOperationName,
        record.scope.name, record.originalMode.name, record.recordedMode.name,
        record.timeMillis, record.removedAtMillis,
    ).joinToString(SEPARATOR)

    fun decode(value: String): AppOpChangeRecord? {
        val fields = value.split(SEPARATOR)
        // The ninth field is only written for a record of a removed package.
        if (fields.size !in 8..9 || fields[0] != V1) return null
        return AppOpChangeRecord(
            userId = fields[1].toIntOrNull()?.takeIf { it >= 0 } ?: return null,
            packageName = fields[2].takeIf(String::isNotBlank) ?: return null,
            stableOperationName = fields[3].takeIf(String::isNotBlank) ?: return null,
            scope = enumOrNull<AppOpScope>(fields[4]) ?: return null,
            originalMode = enumOrNull<AppOpMode>(fields[5]) ?: return null,
            recordedMode = enumOrNull<AppOpMode>(fields[6]) ?: return null,
            timeMillis = fields[7].toLongOrNull() ?: return null,
            removedAtMillis = fields.getOrNull(8)?.let { it.toLongOrNull() ?: return null },
        )
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
        enumValues<T>().firstOrNull { it.name == name }

    private const val SEPARATOR = "\t"
    private const val V1 = "C1"
}
