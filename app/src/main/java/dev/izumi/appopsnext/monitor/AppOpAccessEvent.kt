package dev.izumi.appopsnext.monitor

enum class AppOpAccessKind {
    /** A long-running access started, such as the camera being opened. */
    ACTIVE,

    /** A one-shot access was noted, such as the clipboard being read. */
    NOTED,

    /** A long-running access was attempted, allowed or refused. */
    STARTED,
}

internal data class AppOpAccessEvent(
    val opCode: Int,
    val uid: Int,
    val packageName: String,
    val kind: AppOpAccessKind,
    val allowed: Boolean,
)

/** An access resolved for display, with the operation's shell name attached. */
data class MonitoredAccess(
    val uid: Int,
    val operationName: String,
    val packageName: String,
    val appLabel: String,
    val kind: AppOpAccessKind,
    val allowed: Boolean,
    val observedAtMillis: Long,
    val elapsedRealtimeMillis: Long,
    /** How many times this access was reported, which a throttle can limit. */
    val count: Int = 1,
)

/** Which parts of the watch are live, so a partial registration stays visible. */
data class MonitorStatus(
    val activeWatch: Boolean,
    val notedWatch: Boolean,
    val startedWatch: Boolean,
    val transactionCodesFromPlatform: Boolean,
    val watchedPackages: Int,
    val watchedOperations: Int,
) {
    val partial: Boolean
        get() = !(activeWatch && notedWatch && startedWatch)
}

sealed interface MonitorSelfCheckResult {
    data object Passed : MonitorSelfCheckResult

    /**
     * Registration succeeded but no event arrived. Without WATCH_APPOPS the
     * system narrows a watch to the caller's own UID instead of refusing it, so
     * a silent result cannot be reported as success.
     */
    data object NoEvent : MonitorSelfCheckResult

    data class Failed(val reason: String) : MonitorSelfCheckResult
}
