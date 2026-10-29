package dev.izumi.appopsnext.presentation.settings

import android.net.Uri
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.presentation.experimental.modeTitle
import dev.izumi.appopsnext.backup.BackupPreview
import dev.izumi.appopsnext.backup.BackupProblem
import dev.izumi.appopsnext.presentation.components.AppBottomSheet
import dev.izumi.appopsnext.presentation.components.MainPageChevron
import dev.izumi.appopsnext.presentation.components.MainPageEntryIcon
import java.time.LocalDate

private const val BACKUP_MIME_TYPE = "text/plain"

/** The export and import rows, and every sheet the two open. */
@Composable
internal fun BackupEntries(
    state: BackupUiState,
    onExport: (Uri) -> Unit,
    onOpen: (Uri) -> Unit,
    onConfirmImport: () -> Unit,
    onDismiss: () -> Unit,
    compact: Boolean = false,
) {
    var showActions by rememberSaveable { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE),
    ) { uri -> uri?.let(onExport) }
    // Any type is offered: file managers and cloud drives often label a text file loosely.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onOpen) }
    val working = state == BackupUiState.Working

    val entries: @Composable () -> Unit = {
        ListItem(
            modifier = Modifier.clickable(enabled = !working) {
                // The picker's result opens its own sheet, so this one closes first.
                showActions = false
                exportLauncher.launch("AppOpsNext-backup-${LocalDate.now()}.txt")
            },
            leadingContent = { MainPageEntryIcon(R.drawable.ic_ph_archive) },
            headlineContent = { Text(text = stringResource(R.string.settings_backup_export)) },
            supportingContent = { Text(text = stringResource(R.string.settings_backup_export_detail)) },
            trailingContent = { MainPageChevron() },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        Spacer(Modifier.height(4.dp))
        ListItem(
            modifier = Modifier.clickable(enabled = !working) {
                showActions = false
                importLauncher.launch(arrayOf("*/*"))
            },
            leadingContent = { MainPageEntryIcon(R.drawable.ic_ph_folder_simple) },
            headlineContent = { Text(text = stringResource(R.string.settings_backup_import)) },
            supportingContent = { Text(text = stringResource(R.string.settings_backup_import_detail)) },
            trailingContent = { MainPageChevron() },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )

    }
    if (compact) {
        ListItem(
            modifier = Modifier.clickable { showActions = true },
            leadingContent = { MainPageEntryIcon(R.drawable.ic_ph_folder_simple) },
            headlineContent = { Text(stringResource(R.string.settings_backup_section)) },
            supportingContent = { Text(stringResource(R.string.settings_backup_summary)) },
            trailingContent = { MainPageChevron() },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        if (showActions) {
            AppBottomSheet(
                onDismissRequest = { showActions = false },
                title = { Text(stringResource(R.string.settings_backup_section)) },
                text = { Column(Modifier.verticalScroll(rememberScrollState())) { entries() } },
                confirmButton = {
                    TextButton(onClick = { showActions = false }) { Text(stringResource(R.string.action_dismiss)) }
                },
            )
        }
    } else {
        entries()
    }

    when (state) {
        is BackupUiState.Preview -> BackupPreviewSheet(
            preview = state.preview,
            onConfirm = onConfirmImport,
            onDismiss = onDismiss,
        )
        is BackupUiState.Rejected -> AppBottomSheet(
            onDismissRequest = onDismiss,
            title = { Text(text = stringResource(R.string.backup_rejected_title)) },
            text = { Text(text = stringResource(rejectionMessage(state.problem))) },
            confirmButton = {
                Button(onClick = onDismiss) {
                    Text(text = stringResource(R.string.action_got_it))
                }
            },
        )
        is BackupUiState.Finished -> {
            val context = LocalContext.current
            val message = stringResource(
                when (state.outcome) {
                    BackupOutcome.EXPORTED -> R.string.backup_exported
                    BackupOutcome.EXPORT_FAILED -> R.string.backup_export_failed
                    BackupOutcome.IMPORTED -> R.string.backup_imported
                    BackupOutcome.IMPORT_FAILED -> R.string.backup_import_failed
                },
            )
            LaunchedEffect(state) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        }
        BackupUiState.Idle, BackupUiState.Working -> Unit
    }
}

@Composable
private fun BackupPreviewSheet(
    preview: BackupPreview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showDetails by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val backup = preview.backup
    AppBottomSheet(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.backup_preview_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                backup.createdMillis?.let { millis ->
                    val date = DateFormat.getMediumDateFormat(context).format(millis)
                    Text(
                        text = backup.appVersion?.let {
                            stringResource(R.string.backup_preview_source, date, it)
                        } ?: stringResource(R.string.backup_preview_source_date, date),
                    )
                }
                preview.templates?.let { templates ->
                    Text(
                        text = stringResource(
                            R.string.backup_preview_templates,
                            templates.added,
                            templates.unchanged,
                        ),
                    )
                    if (templates.builtInUpdated) {
                        Text(text = stringResource(R.string.backup_preview_new_app_defaults))
                    }
                }
                if (backup.settings != null) {
                    Text(text = stringResource(R.string.backup_preview_settings))
                }
                if (preview.monitorAppCount != null || preview.monitorPointCount != null) {
                    Text(
                        text = stringResource(
                            R.string.backup_preview_monitor,
                            preview.monitorAppCount ?: 0,
                            preview.monitorPointCount ?: 0,
                        ),
                    )
                }
                preview.guard?.let { guard ->
                    Text(text = stringResource(R.string.backup_preview_guard, guard.backup.rules.size))
                    guard.backup.mode?.let { mode ->
                        Text(text = stringResource(R.string.backup_preview_guard_mode, stringResource(modeTitle(mode))))
                    }
                    if (guard.skipped > 0) {
                        Text(text = stringResource(R.string.backup_preview_guard_skipped, guard.skipped))
                    }
                }
                if (backup.skippedSections.isNotEmpty()) {
                    Text(text = stringResource(R.string.backup_preview_skipped))
                }
            }
        },
        leadingButton = {
            TextButton(onClick = { showDetails = true }) {
                Text(text = stringResource(R.string.backup_details))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = stringResource(R.string.backup_import_action))
            }
        },
    )

    if (showDetails) {
        AppBottomSheet(
            onDismissRequest = { showDetails = false },
            title = { Text(text = stringResource(R.string.backup_details_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = stringResource(R.string.backup_details_contents))
                    Text(text = stringResource(R.string.backup_details_merge))
                    Text(text = stringResource(R.string.backup_details_excluded))
                }
            },
            confirmButton = {
                Button(onClick = { showDetails = false }) {
                    Text(text = stringResource(R.string.action_got_it))
                }
            },
        )
    }
}

private fun rejectionMessage(problem: BackupProblem?): Int =
    when (problem) {
        BackupProblem.FOREIGN -> R.string.backup_rejected_foreign
        BackupProblem.NEWER_FORMAT -> R.string.backup_rejected_newer
        BackupProblem.CORRUPT -> R.string.backup_rejected_corrupt
        BackupProblem.NOTHING_TO_IMPORT -> R.string.backup_rejected_empty
        null -> R.string.backup_rejected_unreadable
    }
