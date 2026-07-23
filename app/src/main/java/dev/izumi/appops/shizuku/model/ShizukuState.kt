package dev.izumi.appops.shizuku.model

enum class ShizukuFailureReason {
    STATE_READ_FAILED,
    PERMISSION_REQUEST_FAILED,
}

sealed interface ShizukuState {
    data object Checking : ShizukuState

    data class Unavailable(
        val isInstalled: Boolean,
    ) : ShizukuState

    data object Unsupported : ShizukuState

    data object PermissionRequired : ShizukuState

    data object PermissionDenied : ShizukuState

    data class Ready(
        val serverVersion: Int,
        val serverUid: Int,
    ) : ShizukuState

    data class Failure(
        val reason: ShizukuFailureReason,
    ) : ShizukuState
}
