package dev.izumi.appopsnext.appops.parser

/**
 * Reads the process state AppOps keeps for each UID out of a package dump.
 *
 * ```
 *   Uid u0a601:
 *     state=top
 *     capability=LCMNFUA
 * ```
 *
 * This is the platform's own notion of how foreground an application is — the
 * one it evaluates `MODE_FOREGROUND` against — rather than a guess made from
 * which window has focus.
 */
object UidStateParser {
    /**
     * The app is the one on screen. Everything else, including a foreground
     * service, is something the user is not currently looking at.
     */
    const val STATE_TOP = "top"

    private val UID_LINE = Regex("""^\s*Uid\s+(\S+?):\s*$""")
    private val STATE_LINE = Regex("""^\s*state=(\S+)\s*$""")

    /** @return each UID in the dump and the state reported for it. */
    fun parse(output: String): Map<Int, String> {
        val states = mutableMapOf<Int, String>()
        var uid: Int? = null
        for (line in output.lineSequence()) {
            UID_LINE.find(line)?.let { match ->
                uid = uidOf(match.groupValues[1])
                return@let
            }
            val current = uid ?: continue
            STATE_LINE.find(line)?.let { match ->
                // Only the first state after a heading belongs to that UID.
                states.putIfAbsent(current, match.groupValues[1])
                uid = null
            }
        }
        return states
    }

    /**
     * `1000` for a system UID, `u0a601` for an application one.
     *
     * The letter form is how the platform prints an application UID: the user
     * id, then the app id counted from the first application UID. Any other
     * form, such as an isolated process, is not something a monitoring point
     * can name, so it is skipped rather than guessed at.
     */
    fun uidOf(text: String): Int? {
        text.toIntOrNull()?.let { return it.takeIf { value -> value >= 0 } }
        val match = APP_UID.find(text) ?: return null
        val userId = match.groupValues[1].toIntOrNull() ?: return null
        val appId = match.groupValues[2].toIntOrNull() ?: return null
        if (appId >= PER_USER_RANGE - FIRST_APPLICATION_UID) return null
        return (userId.toLong() * PER_USER_RANGE + FIRST_APPLICATION_UID + appId)
            .takeIf { it <= Int.MAX_VALUE }?.toInt()
    }

    private val APP_UID = Regex("""^u(\d+)a(\d+)$""")
    private const val PER_USER_RANGE = 100_000
    private const val FIRST_APPLICATION_UID = 10_000
}
