package dev.izumi.appopsnext.history

/**
 * Compacts the large `dumpsys appops --history` response before it crosses
 * Binder. Android only emits per-access discrete history for selected AppOps;
 * other operations, including clipboard reads, are retained in time-bucketed
 * snapshots instead.
 */
object AppOpsHistoryOutputExtractor {
    fun extract(
        commandOutput: String,
        operationName: String,
    ): String {
        val discreteSectionStart =
            commandOutput.indexOf(DISCRETE_SECTION_HEADER)
        val aggregateSource = if (discreteSectionStart >= 0) {
            commandOutput.substring(0, discreteSectionStart)
        } else {
            commandOutput
        }
        val discreteSection = if (discreteSectionStart >= 0) {
            commandOutput.substring(discreteSectionStart)
        } else {
            ""
        }
        val aggregateEntries = extractAggregateEntries(
            commandOutput = aggregateSource,
            operationName = operationName,
        )

        return buildString {
            if (aggregateEntries.isNotEmpty()) {
                appendLine(AGGREGATED_SECTION_HEADER)
                aggregateEntries.forEach { entry ->
                    append(entry)
                }
            }
            if (discreteSection.isNotEmpty()) {
                append(discreteSection)
            }
        }
    }

    private fun extractAggregateEntries(
        commandOutput: String,
        operationName: String,
    ): List<String> {
        val entries = mutableListOf<String>()
        var begin: String? = null
        var end: String? = null
        var uid: String? = null
        var packageName: String? = null
        var attribution: String? = null
        var currentOperation: String? = null

        commandOutput.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line == SNAPSHOT_HEADER -> {
                    begin = null
                    end = null
                    uid = null
                    packageName = null
                    attribution = null
                    currentOperation = null
                }

                line.startsWith(BEGIN_PREFIX) -> {
                    begin = line.removePrefix(BEGIN_PREFIX).trim()
                }

                line.startsWith(END_PREFIX) -> {
                    end = line.removePrefix(END_PREFIX).trim()
                }

                UID_PATTERN.matches(line) -> {
                    uid = UID_PATTERN.matchEntire(line)?.groupValues?.get(1)
                    packageName = null
                    attribution = null
                    currentOperation = null
                }

                PACKAGE_PATTERN.matches(line) -> {
                    packageName = PACKAGE_PATTERN
                        .matchEntire(line)
                        ?.groupValues
                        ?.get(1)
                    attribution = null
                    currentOperation = null
                }

                ATTRIBUTION_PATTERN.matches(line) -> {
                    attribution = ATTRIBUTION_PATTERN
                        .matchEntire(line)
                        ?.groupValues
                        ?.get(1)
                    currentOperation = null
                }

                OPERATION_PATTERN.matches(line) -> {
                    currentOperation = line.removeSuffix(":")
                }

                currentOperation == operationName &&
                    VALUE_PATTERN.matches(line) -> {
                    val entryBegin = begin ?: return@forEach
                    val entryEnd = end ?: return@forEach
                    val entryUid = uid ?: return@forEach
                    val entryPackage = packageName ?: return@forEach
                    val entryAttribution =
                        attribution ?: return@forEach
                    entries += buildString {
                        appendLine("  $SNAPSHOT_HEADER")
                        appendLine("    $BEGIN_PREFIX$entryBegin")
                        appendLine("    $END_PREFIX$entryEnd")
                        appendLine("    Uid $entryUid:")
                        appendLine("      Package $entryPackage:")
                        appendLine(
                            "        Attribution $entryAttribution:",
                        )
                        appendLine("          $operationName:")
                        appendLine("            $line")
                    }
                }
            }
        }
        return entries
    }

    const val AGGREGATED_SECTION_HEADER = "Aggregated accesses:"
    private const val DISCRETE_SECTION_HEADER = "Discrete accesses:"
    private const val SNAPSHOT_HEADER = "snapshot:"
    private const val BEGIN_PREFIX = "begin = "
    private const val END_PREFIX = "end = "

    private val UID_PATTERN = Regex("""Uid\s+([^:]+):""")
    private val PACKAGE_PATTERN = Regex("""Package\s+([^:]+):""")
    private val ATTRIBUTION_PATTERN = Regex("""Attribution\s+(.+):""")
    private val OPERATION_PATTERN = Regex("""[A-Z][A-Z0-9_]*:""")
    private val VALUE_PATTERN = Regex("""\[[^\]]+]\s*=\s*.+""")
}
