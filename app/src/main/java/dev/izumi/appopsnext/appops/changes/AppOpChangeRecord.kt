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
    /**
     * Something outside this app was seen to change the mode since. It stays
     * set when the mode comes back to [recordedMode], because the setting is
     * then as someone else left it, until this app writes it again.
     */
    val changedElsewhere: Boolean = false,
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

    /** The mode is still the one this app set, and nothing else has changed it since. */
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
        // A new record, so a write here takes back a setting changed elsewhere.
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

    /**
     * The newest change to any operation of a package, for sorting. A record
     * changed elsewhere does not count, since the setting is no longer this
     * app's change.
     */
    fun latestTime(
        records: List<AppOpChangeRecord>,
        userId: Int,
        packageName: String,
        firstInstallTimeMillis: Long?,
        operationName: String? = null,
    ): Long? {
        val stableName = operationName?.let(AppOpNames::stableName)
        return forPackage(records, userId, packageName, firstInstallTimeMillis)
            .filter { !it.changedElsewhere && (stableName == null || it.stableOperationName == stableName) }
            .maxOfOrNull(AppOpChangeRecord::timeMillis)
    }

    /**
     * Compares the records of one operation with the modes on the device.
     * [currentMode] gives the mode at a scope, [AppOpMode.DEFAULT] where none
     * is stored and null where the stored mode has no known name. A change
     * elsewhere at any scope outweighs the scopes still as this app left them.
     * A record once changed elsewhere is not marked again when the mode comes
     * back to it, so with only such records left there is no status.
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
        return records.firstOrNull { !it.changedElsewhere }?.let(AppOpChangeStatus::Modified)
    }

    /**
     * Flags the records in [seen] as changed elsewhere. A record that differs
     * from the one seen, for one because this app wrote the setting again
     * since, is left as it is. Returns [records] itself when nothing changes.
     */
    fun markChangedElsewhere(
        records: List<AppOpChangeRecord>,
        seen: Collection<AppOpChangeRecord>,
    ): List<AppOpChangeRecord> {
        val targets = seen.mapTo(HashSet()) { it.copy(changedElsewhere = false) }
        if (records.none { !it.changedElsewhere && it in targets }) return records
        return records.map {
            if (!it.changedElsewhere && it in targets) it.copy(changedElsewhere = true) else it
        }
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
     *
     * An operation whose chosen record was changed elsewhere is left out. Its
     * last mode was not this app's, and the record does not know which it was.
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
        .filterNot(AppOpChangeRecord::changedElsewhere)
        .sortedBy(AppOpChangeRecord::stableOperationName)
}

/**
 * Tab-separated records, one per string, like the guard codec. A record that
 * does not parse is dropped rather than failing the whole log.
 *
 * A record changed elsewhere is written as [V2], which has the removal time
 * in a field of its own, empty while there is none, and the flag after it.
 * Every other record stays [V1], so an older version of this app still reads
 * it, and records written before the flag read as not changed elsewhere.
 */
internal object AppOpChangeCodec {
    fun encode(record: AppOpChangeRecord): String {
        val common = listOf(
            record.userId, record.packageName, record.stableOperationName,
            record.scope.name, record.originalMode.name, record.recordedMode.name,
            record.timeMillis,
        )
        val fields = if (record.changedElsewhere) {
            listOf(V2) + common + listOf(record.removedAtMillis?.toString().orEmpty(), FLAG_SET)
        } else {
            listOf(V1) + common + listOfNotNull(record.removedAtMillis)
        }
        return fields.joinToString(SEPARATOR)
    }

    fun decode(value: String): AppOpChangeRecord? {
        val fields = value.split(SEPARATOR)
        val changedElsewhere = when {
            // The ninth field is only written for a record of a removed package.
            fields[0] == V1 && fields.size in 8..9 -> false
            fields[0] == V2 && fields.size == 10 -> when (fields[9]) {
                FLAG_SET -> true
                FLAG_CLEAR -> false
                else -> return null
            }
            else -> return null
        }
        return AppOpChangeRecord(
            userId = fields[1].toIntOrNull()?.takeIf { it >= 0 } ?: return null,
            packageName = fields[2].takeIf(String::isNotBlank) ?: return null,
            stableOperationName = fields[3].takeIf(String::isNotBlank) ?: return null,
            scope = enumOrNull<AppOpScope>(fields[4]) ?: return null,
            originalMode = enumOrNull<AppOpMode>(fields[5]) ?: return null,
            recordedMode = enumOrNull<AppOpMode>(fields[6]) ?: return null,
            timeMillis = fields[7].toLongOrNull() ?: return null,
            removedAtMillis = fields.getOrNull(8)?.takeUnless { fields[0] == V2 && it.isEmpty() }
                ?.let { it.toLongOrNull() ?: return null },
            changedElsewhere = changedElsewhere,
        )
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
        enumValues<T>().firstOrNull { it.name == name }

    private const val SEPARATOR = "\t"
    private const val V1 = "C1"
    private const val V2 = "C2"
    private const val FLAG_SET = "1"
    private const val FLAG_CLEAR = "0"
}
