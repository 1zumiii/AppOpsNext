package dev.izumi.appopsnext.appops.binder

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpEntry
import dev.izumi.appopsnext.appops.model.PackageOpsSnapshot
import dev.izumi.appopsnext.appops.parser.OpStates

/** Times are epoch milliseconds and the duration is milliseconds; -1 means none. */
internal data class RawOpEntry(
    val code: Int,
    val mode: Int,
    val lastAccessTime: Long = -1,
    val lastRejectTime: Long = -1,
    val lastDuration: Long = -1,
)

/** `packageName` is null for the UID-level records `getUidOps` returns. */
internal data class RawPackageOps(
    val packageName: String?,
    val uid: Int,
    val ops: List<RawOpEntry>,
)

/**
 * Turns binder records into the models the text parsers produce, so callers
 * cannot tell the two paths apart. Any code or mode it cannot name makes the
 * whole result null: a partial snapshot would look complete to the UI.
 */
internal object StructuredOpsMapper {
    /** `AppOpsManager.MODE_*`, the values `cmd appops` prints by name. */
    fun modeName(mode: Int): String? = MODE_NAMES[mode]

    /** Entries in `cmd appops get <package>` order: UID modes, then package modes. */
    fun packageSnapshot(
        packageName: String,
        uidOps: List<RawPackageOps>,
        packageOps: List<RawPackageOps>,
        nameOf: (Int) -> String?,
        nowMillis: Long,
    ): PackageOpsSnapshot? {
        val uidEntries = uidOps.flatMap { it.ops }
            .map { entry(it, nameOf, uidMode = true, nowMillis) ?: return null }
        val packageEntries = packageOps
            .filter { it.packageName == packageName }
            .flatMap { it.ops }
            .map { entry(it, nameOf, uidMode = false, nowMillis) ?: return null }
        return PackageOpsSnapshot(
            packageName = packageName,
            entries = uidEntries + packageEntries,
            rawOutput = "",
        )
    }

    /** A missing record is the default mode, as with the text read. */
    fun mode(records: List<RawPackageOps>, code: Int): AppOpMode? {
        val entry = records.flatMap { it.ops }.firstOrNull { it.code == code }
            ?: return AppOpMode.DEFAULT
        return modeName(entry.mode)?.let(AppOpMode::fromShellValue)
    }

    /** Matches [dev.izumi.appopsnext.appops.parser.OpStatesParser]: only UIDs of [userId]. */
    fun opStates(
        code: Int,
        userId: Int,
        packages: List<RawPackageOps>,
        uidRecords: List<RawPackageOps>,
    ): OpStates? {
        val uidModes = mutableMapOf<Int, String>()
        for (record in uidRecords) {
            if (record.uid / PER_USER_RANGE != userId) continue
            val entry = record.ops.firstOrNull { it.code == code } ?: continue
            uidModes[record.uid] = modeName(entry.mode) ?: return null
        }
        val packageModes = mutableMapOf<Pair<Int, String>, String>()
        for (record in packages) {
            val packageName = record.packageName ?: continue
            if (record.uid / PER_USER_RANGE != userId) continue
            val entry = record.ops.firstOrNull { it.code == code } ?: continue
            packageModes[record.uid to packageName] = modeName(entry.mode) ?: return null
        }
        return OpStates(uidModes, packageModes)
    }

    private fun entry(
        raw: RawOpEntry,
        nameOf: (Int) -> String?,
        uidMode: Boolean,
        nowMillis: Long,
    ): AppOpEntry? {
        val name = nameOf(raw.code) ?: return null
        val mode = modeName(raw.mode) ?: return null
        return AppOpEntry(
            name = name,
            mode = mode,
            details = if (uidMode) null else details(raw, nowMillis),
            hasUidModePrefix = uidMode,
        )
    }

    /** The `time=+2m1s ago; duration=+5s` text `cmd appops get` prints after the mode. */
    fun details(raw: RawOpEntry, nowMillis: Long): String? =
        buildList {
            if (raw.lastAccessTime > 0) add("time=${elapsed(nowMillis - raw.lastAccessTime)} ago")
            if (raw.lastRejectTime > 0) add("rejectTime=${elapsed(nowMillis - raw.lastRejectTime)} ago")
            if (raw.lastDuration > 0) add("duration=${elapsed(raw.lastDuration)}")
        }.takeIf { it.isNotEmpty() }?.joinToString("; ")

    /** `TimeUtils.formatDuration` spelling: `+1d2h3m4s5ms`, zero units left out. */
    fun elapsed(millis: Long): String {
        var rest = millis.coerceAtLeast(0)
        val text = buildString {
            for ((unit, size) in ELAPSED_UNITS) {
                val value = rest / size
                if (value > 0) append(value).append(unit)
                rest %= size
            }
        }
        return "+" + text.ifEmpty { "0ms" }
    }

    private val ELAPSED_UNITS = listOf(
        "d" to 86_400_000L,
        "h" to 3_600_000L,
        "m" to 60_000L,
        "s" to 1_000L,
        "ms" to 1L,
    )

    private val MODE_NAMES = mapOf(
        0 to AppOpMode.ALLOW.shellValue,
        1 to AppOpMode.IGNORE.shellValue,
        2 to AppOpMode.DENY.shellValue,
        3 to AppOpMode.DEFAULT.shellValue,
        4 to AppOpMode.FOREGROUND.shellValue,
    )

    /** Android's `UserHandle.PER_USER_RANGE`. */
    private const val PER_USER_RANGE = 100_000
}
