package dev.izumi.appopsnext.shizuku.model

data class PrivilegedServiceInfo(
    val uid: Int,
    val pid: Int,
    val apiLevel: Int,
    val backendType: PrivilegedBackendType,
)

enum class PrivilegedBackendType {
    NATIVE_DAEMON,
    USER_SERVICE,
}

enum class PrivilegedServiceFailureReason {
    EMPTY_BINDER,
    INITIALIZATION_FAILED,
    BIND_FAILED,
    BIND_TIMED_OUT,
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
