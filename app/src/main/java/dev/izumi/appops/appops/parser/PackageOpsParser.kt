package dev.izumi.appops.appops.parser

import dev.izumi.appops.appops.model.AppOpEntry
import dev.izumi.appops.appops.model.PackageOpsSnapshot

class PackageOpsParser {
    fun parse(
        packageName: String,
        rawOutput: String,
    ): PackageOpsSnapshot {
        val entries = rawOutput
            .lineSequence()
            .mapNotNull(::parseLine)
            .toList()

        return PackageOpsSnapshot(
            packageName = packageName,
            entries = entries,
            rawOutput = rawOutput,
        )
    }

    internal fun parseLine(rawLine: String): AppOpEntry? {
        val line = rawLine.trim()
        if (line.isEmpty() || line == NO_OPERATIONS_LINE) return null

        val hasUidModePrefix = line.startsWith(UID_MODE_PREFIX)
        val entryText = line.removePrefix(UID_MODE_PREFIX)
        val separatorIndex = entryText.indexOf(MODE_SEPARATOR)
        if (separatorIndex <= 0) return null

        val name = entryText.substring(0, separatorIndex).trim()
        if (!operationNamePattern.matches(name)) return null

        val modeAndDetails = entryText
            .substring(separatorIndex + MODE_SEPARATOR.length)
            .trim()
        val mode = modeAndDetails
            .substringBefore(';')
            .substringBefore(' ')
            .trim()
        if (!modePattern.matches(mode)) return null

        val details = modeAndDetails
            .removePrefix(mode)
            .trim()
            .removePrefix(";")
            .trim()
            .ifEmpty { null }

        return AppOpEntry(
            name = name,
            mode = mode,
            details = details,
            hasUidModePrefix = hasUidModePrefix,
        )
    }

    private companion object {
        const val UID_MODE_PREFIX = "Uid mode: "
        const val MODE_SEPARATOR = ": "
        const val NO_OPERATIONS_LINE = "No operations."

        val operationNamePattern = Regex("""[A-Za-z0-9_.:-]+""")
        val modePattern = Regex("""[a-z_]+""")
    }
}
