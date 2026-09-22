package dev.izumi.appopsnext.monitor

import dev.izumi.appopsnext.appops.parser.WatchRegistration

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
    val registry: WatchRegistration = WatchRegistration.UNKNOWN,
    val callbackFailure: String? = null,
) {
    val partial: Boolean
        get() = !(activeWatch && notedWatch && startedWatch)
}

sealed interface MonitorSelfCheckResult {
    data object Passed : MonitorSelfCheckResult

    /** The registry could not establish coverage; the user may explicitly proceed. */
    data object Unconfirmed : MonitorSelfCheckResult

    data class Failed(val reason: String) : MonitorSelfCheckResult
}
