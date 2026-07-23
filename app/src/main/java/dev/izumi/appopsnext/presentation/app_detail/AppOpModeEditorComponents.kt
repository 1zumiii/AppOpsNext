package dev.izumi.appopsnext.presentation.app_detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpEntry
import dev.izumi.appopsnext.appops.model.AppOpModeChangePhase
import dev.izumi.appopsnext.appops.model.AppOpsRestorationStatus

@Composable
internal fun AppOpListItem(
    entry: AppOpEntry,
    editEnabled: Boolean,
    onModeSelected: (AppOpMode, AppOpMode) -> Unit,
) {
    val currentMode = AppOpMode.fromShellValue(entry.mode)
    ListItem(
        headlineContent = {
            Text(
                text = entry.name,
                fontWeight = FontWeight.Medium,
            )
        },
        supportingContent = entry.details?.let { details ->
            {
                Text(
                    text = details,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        overlineContent = {
            Text(
                text = stringResource(
                    if (entry.hasUidModePrefix) {
                        R.string.app_detail_scope_uid
                    } else {
                        R.string.app_detail_scope_package
                    },
                ),
            )
        },
        trailingContent = {
            if (!entry.hasUidModePrefix && currentMode != null) {
                EditableModeMenu(
                    currentMode = currentMode,
                    enabled = editEnabled,
                    onModeSelected = onModeSelected,
                )
            } else {
                Text(
                    text = modeLabelOrRaw(entry.mode),
                    color = if (entry.hasUidModePrefix) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    )
}

@Composable
private fun EditableModeMenu(
    currentMode: AppOpMode,
    enabled: Boolean,
    onModeSelected: (AppOpMode, AppOpMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            enabled = enabled,
        ) {
            Text(
                text = stringResource(
                    R.string.app_detail_mode_button,
                    modeLabel(currentMode),
                ),
                fontWeight = FontWeight.SemiBold,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            AppOpMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(text = modeLabel(mode)) },
                    enabled = mode != currentMode,
                    onClick = {
                        expanded = false
                        onModeSelected(currentMode, mode)
                    },
                )
            }
        }
    }
}

@Composable
internal fun ModeChangeDialog(
    state: AppOpModeChangeUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is AppOpModeChangeUiState.Confirming -> AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(text = stringResource(R.string.app_detail_mode_confirm_title))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(
                            R.string.app_detail_mode_confirm_operation,
                            state.request.operationName,
                        ),
                    )
                    Text(
                        text = stringResource(
                            R.string.app_detail_mode_transition,
                            modeLabel(state.request.originalMode),
                            modeLabel(state.request.requestedMode),
                        ),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(
                            R.string.app_detail_mode_confirm_warning,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(text = stringResource(R.string.action_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )

        is AppOpModeChangeUiState.Applying -> AlertDialog(
            onDismissRequest = {},
            title = {
                Text(text = stringResource(R.string.app_detail_mode_applying_title))
            },
            text = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(
                            R.string.app_detail_mode_applying_detail,
                            state.request.operationName,
                        ),
                    )
                }
            },
            confirmButton = {},
        )

        else -> Unit
    }
}

@Composable
internal fun ModeChangeResultCard(
    state: AppOpModeChangeUiState,
    onDismiss: () -> Unit,
) {
    val isSuccess = state is AppOpModeChangeUiState.Success
    val containerColor = if (isSuccess) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (state) {
                is AppOpModeChangeUiState.Success -> {
                    Text(
                        text = stringResource(
                            R.string.app_detail_mode_change_success,
                        ),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(
                            R.string.app_detail_mode_change_success_detail,
                            state.request.operationName,
                            modeLabel(state.result.originalMode),
                            modeLabel(state.result.appliedMode),
                        ),
                    )
                }

                is AppOpModeChangeUiState.Failure -> {
                    Text(
                        text = stringResource(
                            R.string.app_detail_mode_change_failure,
                        ),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(
                            R.string.app_detail_mode_change_failure_detail,
                            modeChangePhaseLabel(state.result.phase),
                            restorationStatusLabel(
                                state.result.restorationStatus,
                            ),
                        ),
                    )
                    state.result.observedMode?.let { observedMode ->
                        Text(
                            text = stringResource(
                                R.string.app_detail_mode_observed,
                                modeLabel(observedMode),
                            ),
                        )
                    }
                }

                else -> Unit
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(text = stringResource(R.string.action_dismiss))
            }
        }
    }
}

@Composable
private fun modeLabelOrRaw(rawMode: String): String =
    AppOpMode.fromShellValue(rawMode)?.let { modeLabel(it) } ?: rawMode

@Composable
private fun modeLabel(mode: AppOpMode): String =
    stringResource(
        when (mode) {
            AppOpMode.ALLOW -> R.string.app_op_mode_allow
            AppOpMode.IGNORE -> R.string.app_op_mode_ignore
            AppOpMode.DENY -> R.string.app_op_mode_deny
            AppOpMode.DEFAULT -> R.string.app_op_mode_default
            AppOpMode.FOREGROUND -> R.string.app_op_mode_foreground
        },
    )

@Composable
private fun modeChangePhaseLabel(phase: AppOpModeChangePhase): String =
    stringResource(
        when (phase) {
            AppOpModeChangePhase.READ_ORIGINAL ->
                R.string.app_detail_mode_phase_read_original

            AppOpModeChangePhase.CHECK_ORIGINAL ->
                R.string.app_detail_mode_phase_check_original

            AppOpModeChangePhase.APPLY_REQUESTED ->
                R.string.app_detail_mode_phase_apply

            AppOpModeChangePhase.VERIFY_REQUESTED ->
                R.string.app_detail_mode_phase_verify

            AppOpModeChangePhase.RESTORE_ORIGINAL ->
                R.string.app_detail_mode_phase_restore

            AppOpModeChangePhase.VERIFY_RESTORED ->
                R.string.app_detail_mode_phase_verify_restored
        },
    )

@Composable
private fun restorationStatusLabel(
    status: AppOpsRestorationStatus,
): String =
    stringResource(
        when (status) {
            AppOpsRestorationStatus.NOT_REQUIRED ->
                R.string.app_detail_mode_restore_not_required

            AppOpsRestorationStatus.SUCCEEDED ->
                R.string.app_detail_mode_restored

            AppOpsRestorationStatus.FAILED ->
                R.string.app_detail_mode_restore_failed
        },
    )
