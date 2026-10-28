package dev.izumi.appopsnext.history

import dev.izumi.appopsnext.history.model.AppOpHistoryEvent
import java.text.DateFormat

/**
 * Reads the discrete history table of Android 17, which replaced the nested
 * `Discrete accesses:` blocks:
 *
 * `UID|PACKAGE_NAME|DEVICE_ID|OP_NAME|ATTRIBUTION_TAG|ACCESS_TIME|ACCESS_COUNTS|REJECT_COUNTS|DURATION|UID_STATE|OP_FLAGS|...`
 *
 * Columns are found by name. One access is printed as a row with its count
 * and a row with its duration, at the same time; the rows become events with
 * and without a duration, which DiscreteHistoryEventMerger pairs up again.
 * Rows with only rejections are skipped, as the older format listed accesses only.
 */
class DiscreteHistoryTable(private val columns: List<String>) {
    private fun column(name: String) = columns.indexOf(name).takeIf { it >= 0 }

    private val uid = column("UID")
    private val packageName = column("PACKAGE_NAME")
    private val operation = column(OP_NAME)
    private val attribution = column("ATTRIBUTION_TAG")
    private val accessTime = column("ACCESS_TIME")
    private val accessCount = column("ACCESS_COUNTS")
    private val duration = column("DURATION")
    private val uidState = column("UID_STATE")
    private val flags = column("OP_FLAGS")

    fun event(row: String, operationName: String, dateFormat: DateFormat): AppOpHistoryEvent? {
        val cells = row.split('|')
        if (cells.size != columns.size) return null
        fun cell(index: Int?) = index?.let(cells::get)
        if (cell(operation) != operationName) return null
        val durationMillis = cell(duration)?.toLongOrNull()?.takeIf { it > 0 }
        val count = cell(accessCount)?.toLongOrNull() ?: 0
        if (durationMillis == null && count <= 0) return null
        return AppOpHistoryEvent(
            uid = cell(uid)?.toIntOrNull() ?: return null,
            packageName = cell(packageName)?.takeIf(String::isNotBlank) ?: return null,
            operationName = operationName,
            attributionTag = cell(attribution)?.takeUnless { it == NULL_ATTRIBUTION },
            accessTimeMillis = cell(accessTime)?.let { runCatching { dateFormat.parse(it)?.time }.getOrNull() }
                ?: return null,
            durationMillis = durationMillis,
            uidState = cell(uidState).orEmpty(),
            flags = cell(flags).orEmpty(),
        )
    }

    companion object {
        const val HEADER_PREFIX = "UID|PACKAGE_NAME|"
        const val OP_NAME = "OP_NAME"
        private const val NULL_ATTRIBUTION = "null"
    }
}
