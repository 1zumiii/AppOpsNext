package dev.izumi.appopsnext.history.model

data class AppOpHistoryEvent(
    val uid: Int,
    val packageName: String,
    val operationName: String,
    val attributionTag: String?,
    val accessTimeMillis: Long,
    val durationMillis: Long?,
    val uidState: String,
    val flags: String,
)

enum class TrackedHistoryPermission(
    val shellOperationName: String,
) {
    CAMERA("CAMERA"),
    MICROPHONE("RECORD_AUDIO"),
    PRECISE_LOCATION("FINE_LOCATION"),
    APPROXIMATE_LOCATION("COARSE_LOCATION"),
}

enum class AppOpHistoryFailureReason {
    BACKEND_UNAVAILABLE,
    COMMAND_FAILED,
    COMMAND_TIMED_OUT,
}

sealed interface AppOpHistoryLoadResult {
    data class Success(
        val events: List<AppOpHistoryEvent>,
    ) : AppOpHistoryLoadResult

    data class Failure(
        val reason: AppOpHistoryFailureReason,
    ) : AppOpHistoryLoadResult
}
