package dev.izumi.appopsnext.appops.parser

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpNames
import dev.izumi.appopsnext.appops.model.AppOpScope

data class EffectiveOpState(val mode: AppOpMode, val scope: AppOpScope)

data class OpStates(
    val uidModes: Map<Int, AppOpMode>,
    val packageModes: Map<Pair<Int, String>, AppOpMode>,
) {
    fun effective(uid: Int, packageName: String): EffectiveOpState =
        uidModes[uid]?.let { EffectiveOpState(it, AppOpScope.UID) }
            ?: EffectiveOpState(
                packageModes[uid to packageName] ?: AppOpMode.DEFAULT,
                AppOpScope.PACKAGE,
            )
}

/** Parses only top-level UID records, never watcher or access-history records. */
object OpStatesParser {
    fun parse(output: String, operationName: String, userId: Int): OpStates {
        require(userId >= 0)
        val operation = AppOpNames.shellName(operationName)
        val uidModes = mutableMapOf<Int, AppOpMode>()
        val packageModes = mutableMapOf<Pair<Int, String>, AppOpMode>()
        var uid: Int? = null
        var packageName: String? = null
        output.lineSequence().forEach { line ->
            val uidMatch = uidHeader.matchEntire(line)
            if (uidMatch != null) {
                uid = parseUid(uidMatch.groupValues[1])?.takeIf { it / 100000 == userId }
                packageName = null
            } else if (line.isNotBlank() && line.takeWhile { it == ' ' }.length <= 2) {
                uid = null
                packageName = null
            } else {
                val currentUid = uid ?: return@forEach
                packageHeader.matchEntire(line)?.let {
                    packageName = it.groupValues[1]
                    return@forEach
                }
                uidMode.matchEntire(line)?.let {
                    if (packageName == null && it.groupValues[1] == operation) {
                        uidModes[currentUid] = parseMode(it.groupValues[2])
                    }
                    return@forEach
                }
                packageMode.matchEntire(line)?.let {
                    val pkg = packageName ?: return@forEach
                    if (it.groupValues[1] == operation) {
                        packageModes[currentUid to pkg] = parseMode(it.groupValues[2])
                    }
                }
            }
        }
        return OpStates(uidModes, packageModes)
    }

    private fun parseMode(value: String): AppOpMode =
        requireNotNull(AppOpMode.fromShellValue(value)) { "Unknown AppOps mode: $value" }

    private fun parseUid(value: String): Int? {
        value.toIntOrNull()?.let { return it.takeIf { uid -> uid >= 0 } }
        val match = appUid.matchEntire(value) ?: return null
        val user = match.groupValues[1].toLongOrNull() ?: return null
        val app = match.groupValues[2].toLongOrNull() ?: return null
        if (app >= 90000 || user > Int.MAX_VALUE / 100000) return null
        return (user * 100000 + 10000 + app).takeIf { it <= Int.MAX_VALUE }?.toInt()
    }

    private val uidHeader = Regex("""  Uid (\S+):\s*""")
    private val appUid = Regex("""u(\d+)a(\d+)""")
    private val packageHeader = Regex("""    Package ([\w.]+):\s*""")
    private val uidMode = Regex("""      ([A-Z0-9_]+): mode=(\w+)\s*""")
    private val packageMode = Regex("""      ([A-Z0-9_]+) \((\w+)\):.*""")
}
