package dev.izumi.appopsnext.appops.model

/**
 * Xiaomi's private operations on MIUI and HyperOS, numbered from 10001.
 *
 * `cmd appops get` prints them as `MIUIOP(10008)` but accepts only the bare
 * number, so the number is the name this app keeps for them.
 */
object MiuiOps {
    fun isMiuiOp(operationName: String): Boolean =
        codeOf(operationName) != null

    /** The name for a binder record's code, or null when the code is not a MIUI one. */
    fun nameOf(code: Int): String? =
        code.takeIf { it in CODES }?.toString()

    /** Whether the security center prompts for [operationName] when its mode is ask. */
    fun takesAsk(operationName: String): Boolean =
        codeOf(operationName) in ASK_CODES

    fun codeOf(operationName: String): Int? =
        AppOpNames.shellName(operationName).toIntOrNull()?.takeIf { it in CODES }

    /** `MIUIOP(10008)` as `cmd appops get` prints it, or null for any other text. */
    fun fromPrinted(printedName: String): String? =
        PRINTED_NAME.matchEntire(printedName)?.groupValues?.get(1)?.toIntOrNull()?.let(::nameOf)

    /**
     * A code as the watcher registry may print it: `MIUIOP(10008)` or
     * `MIUI:10008`, the two spellings the framework's `getOpName` produces for
     * these operations. Null for any other text, standard names included.
     */
    fun codeOfPrinted(printedName: String): Int? =
        (PRINTED_NAME.matchEntire(printedName) ?: PREFIXED_NAME.matchEntire(printedName))
            ?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in CODES }

    /**
     * The codes this app has labels for, from the framework's `MiuiHooks` (to
     * 10035) and the security center's `AppOpsManagerCompat` (to 10053).
     */
    val CATALOGUED_CODES: IntRange = 10_001..10_053

    // The framework accepts codes up to 10056 and already notes 10036, past its
    // named constants, so the range is wider than the catalogued codes.
    private val CODES = 10_001..10_999
    // The security center's AppOpsAdapter.askOp handles these MIUI codes, plus
    // the storage operations 59 and 60 that Android 15 no longer applies.
    private val ASK_CODES = setOf(10_022, 10_044, 10_051, 10_052)
    private val PRINTED_NAME = Regex("""MIUIOP\((\d+)\)""")
    private val PREFIXED_NAME = Regex("""MIUI:(\d+)""")
}
