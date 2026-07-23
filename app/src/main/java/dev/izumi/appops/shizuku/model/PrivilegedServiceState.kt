package dev.izumi.appops.shizuku.model

data class PrivilegedServiceInfo(
    val uid: Int,
    val pid: Int,
    val apiLevel: Int,
)

enum class PrivilegedServiceFailureReason {
    EMPTY_BINDER,
    INITIALIZATION_FAILED,
    BIND_FAILED,
}

sealed interface PrivilegedServiceState {
    data object Disconnected : PrivilegedServiceState

    data object Connecting : PrivilegedServiceState

    data class Connected(
        val info: PrivilegedServiceInfo,
    ) : PrivilegedServiceState

    data class Failure(
        val reason: PrivilegedServiceFailureReason,
    ) : PrivilegedServiceState
}
