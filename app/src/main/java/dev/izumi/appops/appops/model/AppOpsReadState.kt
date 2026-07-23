package dev.izumi.appops.appops.model

enum class AppOpsReadFailureReason {
    BACKEND_UNAVAILABLE,
    COMMAND_FAILED,
    COMMAND_TIMED_OUT,
}
sealed interface AppOpsReadState {
    data object WaitingForBackend : AppOpsReadState

    data object Reading : AppOpsReadState

    data class Ready(
        val operationCount: Int,
    ) : AppOpsReadState

    data class Failure(
        val reason: AppOpsReadFailureReason,
    ) : AppOpsReadState
}
