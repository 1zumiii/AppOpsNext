package dev.izumi.appopsnext.presentation.app_detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpModeChangePhase
import dev.izumi.appopsnext.appops.model.AppOpsRestorationStatus
import dev.izumi.appopsnext.appops.model.AppOpScope

@Composable
internal fun AppOpListItem(
    item: AppOpDisplayItem,
    isApplying: Boolean,
    editEnabled: Boolean,
    onModeSelected: (AppOpMode, AppOpMode) -> Unit,
) {
    val currentMode = AppOpMode.fromShellValue(item.mode)
    val context = LocalContext.current
    val usageDetails = AppOpUsageDetailsFormatter.format(
        rawDetails = item.details,
        stringResolver = { resourceId, arguments ->
            context.getString(resourceId, *arguments.toTypedArray())
        },
    )
    ListItem(
        headlineContent = {
            Text(
                text = item.labelRes?.let { stringResource(it) }
                    ?: item.operationName,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        supportingContent = {
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = item.operationName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                usageDetails?.let { details ->
                    Text(
                        text = details,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        overlineContent = {
            Text(
                text = stringResource(
                    when (item.scope) {
                        AppOpScope.UID -> R.string.app_detail_scope_uid
                        AppOpScope.PACKAGE ->
                            R.string.app_detail_scope_package
                    },
                ),
            )
        },
        trailingContent = {
            if (isApplying) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = stringResource(
                            R.string.app_detail_mode_item_applying,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            } else if (currentMode != null) {
                EditableModeMenu(
                    currentMode = currentMode,
                    enabled = editEnabled,
                    onModeSelected = onModeSelected,
                )
            } else {
                Text(
                    text = item.mode,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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
                            when (state.request.scope) {
                                AppOpScope.UID ->
                                    R.string.app_detail_mode_scope_uid

                                AppOpScope.PACKAGE ->
                                    R.string.app_detail_mode_scope_package
                            },
                        ),
                    )
                    if (
                        state.request.scope == AppOpScope.UID &&
                        state.request.affectedPackages.size > 1
                    ) {
                        Text(
                            text = stringResource(
                                R.string.app_detail_mode_shared_uid_warning,
                                state.request.affectedPackages.joinToString(
                                    separator = "\n",
                                ),
                            ),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
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

        else -> Unit
    }
}

@Composable
internal fun ModeChangeFailureCard(
    state: AppOpModeChangeUiState.Failure,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
            if (
                state.result.phase ==
                AppOpModeChangePhase.VERIFY_REQUESTED
            ) {
                Text(
                    text = stringResource(
                        R.string.app_detail_mode_change_rejected_hint,
                    ),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.result.observedMode?.let { observedMode ->
                Text(
                    text = stringResource(
                        R.string.app_detail_mode_observed,
                        modeLabel(observedMode),
                    ),
                )
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
