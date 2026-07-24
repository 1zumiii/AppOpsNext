package dev.izumi.appopsnext.history

object DiscreteHistoryOutputExtractor {
    fun extract(commandOutput: String): String {
        val sectionStart = commandOutput.indexOf(DISCRETE_SECTION_HEADER)
        if (sectionStart < 0) return ""
        return commandOutput.substring(sectionStart)
    }

    private const val DISCRETE_SECTION_HEADER = "Discrete accesses:"
}
