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

data class HistoryPermission(
    val shellOperationName: String,
) {
    init {
        require(shellOperationName.isNotBlank()) {
            "History permission operation name must not be blank"
        }
    }
}

object HistoryPermissionDefaults {
    val permissions = listOf(
        HistoryPermission("CAMERA"),
        HistoryPermission("RECORD_AUDIO"),
        HistoryPermission("FINE_LOCATION"),
        HistoryPermission("COARSE_LOCATION"),
    )
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
