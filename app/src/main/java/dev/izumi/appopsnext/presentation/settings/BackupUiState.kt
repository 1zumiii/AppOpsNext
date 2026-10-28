package dev.izumi.appopsnext.presentation.settings

import dev.izumi.appopsnext.backup.BackupPreview
import dev.izumi.appopsnext.backup.BackupProblem

sealed interface BackupUiState {
    data object Idle : BackupUiState

    data object Working : BackupUiState

    /** A file was read and is waiting for the import to be confirmed. */
    data class Preview(val preview: BackupPreview) : BackupUiState

    /** The file was not imported, and nothing was changed. */
    data class Rejected(val problem: BackupProblem?) : BackupUiState

    /** Reported briefly, then back to [Idle]. */
    data class Finished(val outcome: BackupOutcome) : BackupUiState
}

enum class BackupOutcome {
    EXPORTED,
    EXPORT_FAILED,
    IMPORTED,
    IMPORT_FAILED,
}
