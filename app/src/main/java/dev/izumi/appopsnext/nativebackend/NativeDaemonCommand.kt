package dev.izumi.appopsnext.nativebackend

internal data class NativeDaemonCommand(
    val verb: String,
    val arguments: List<String> = emptyList(),
) {
    init {
        require(verb.matches(TOKEN_PATTERN)) {
            "Invalid native daemon command verb"
        }
        require(arguments.all { it.matches(TOKEN_PATTERN) }) {
            "Native daemon arguments cannot contain whitespace"
        }
    }

    fun encode(): String =
        (listOf(verb) + arguments).joinToString(
            separator = " ",
            postfix = "\n",
        )

    private companion object {
        val TOKEN_PATTERN = Regex("""\S+""")
    }
}
