package dev.izumi.appopsnext.update

sealed interface UpdateState {
    data object Idle : UpdateState

    data object Checking : UpdateState

    data object UpToDate : UpdateState

    data class Available(
        val versionName: String,
        val releaseUrl: String,
    ) : UpdateState

    data object Failed : UpdateState
}
