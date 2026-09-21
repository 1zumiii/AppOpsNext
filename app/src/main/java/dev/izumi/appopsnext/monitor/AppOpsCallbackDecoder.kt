package dev.izumi.appopsnext.monitor

/** Checked reads let the decoder run on the JVM without Android's permissive Parcel EOF reads. */
internal interface AppOpsCallbackInput {
    val remaining: Int
    fun int(): Int
    fun string(): String?
}

internal object AppOpsCallbackDecoder {
    fun decode(kind: AppOpAccessKind, input: AppOpsCallbackInput): AppOpAccessEvent? {
        val op = input.int()
        val uid = input.int()
        val packageName = input.string()
        input.string() // attributionTag
        require(!packageName.isNullOrEmpty()) { "Missing callback package" }
        val oldTailBytes = when (kind) {
            AppOpAccessKind.ACTIVE -> 12
            AppOpAccessKind.NOTED -> 8
            AppOpAccessKind.STARTED -> 20
        }
        when (input.remaining) {
            oldTailBytes -> Unit
            oldTailBytes + 4 -> input.int() // virtualDeviceId
            else -> error("Invalid $kind tail length: ${input.remaining}")
        }
        val allowed = when (kind) {
            AppOpAccessKind.ACTIVE -> {
                val active = input.int()
                require(active == 0 || active == 1) { "Invalid active flag" }
                input.int() // attributionFlags
                input.int() // attributionChainId
                // Only the start of an access is reported; the matching stop
                // would otherwise raise a second, redundant entry.
                if (active == 0) return null
                true
            }
            AppOpAccessKind.NOTED, AppOpAccessKind.STARTED -> {
                input.int() // flags
                val mode = input.int()
                // The tail length already identifies the layout, so this only has to
                // reject nonsense. Vendor images define modes of their own above
                // MODE_FOREGROUND, and dropping those events would make the monitor
                // look silent on exactly the devices that need it most.
                require(mode >= 0) { "Invalid AppOps mode: $mode" }
                if (kind == AppOpAccessKind.STARTED) {
                    input.int() // startedType
                    input.int() // attributionFlags
                    input.int() // attributionChainId
                }
                mode == MODE_ALLOWED
            }
        }
        check(input.remaining == 0) { "Trailing callback data" }
        return AppOpAccessEvent(op, uid, packageName, kind, allowed)
    }

    /** Anything else, including a vendor's own modes, counts as not allowed. */
    private const val MODE_ALLOWED = 0
}
