package dev.izumi.appopsnext.presentation.batch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpModeChangePhase
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.batch.model.BatchOperationItemResult

@Composable
fun BatchOperationDialog(
    state: BatchOperationUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        BatchOperationUiState.Idle -> Unit

        is BatchOperationUiState.Confirming -> AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(text = stringResource(R.string.batch_confirm_title))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(
                            R.string.batch_confirm_template,
                            state.request.title,
                        ),
                    )
                    Text(
                        text = stringResource(
                            R.string.batch_confirm_summary,
                            state.request.targetCount,
                            state.request.operationCount,
                        ),
                    )
                    Text(
                        text = stringResource(
                            R.string.batch_confirm_warning,
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

        is BatchOperationUiState.Running -> AlertDialog(
            onDismissRequest = {},
            title = {
                Text(text = stringResource(R.string.batch_running_title))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(
                        progress = {
                            if (state.request.operationCount == 0) {
                                0f
                            } else {
                                state.completed.toFloat() /
                                    state.request.operationCount
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(
                            R.string.batch_running_progress,
                            state.completed,
                            state.request.operationCount,
                        ),
                    )
                }
            },
            confirmButton = {},
        )

        is BatchOperationUiState.Finished -> AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(text = stringResource(R.string.batch_result_title))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(
                            R.string.batch_result_operation,
                            state.report.title,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            R.string.batch_result_summary,
                            state.report.successCount,
                            state.report.failureCount,
                        ),
                        fontWeight = FontWeight.SemiBold,
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                    ) {
                        items(
                            items = state.report.results,
                            key = { item ->
                                listOf(
                                    item.target.packageName,
                                    item.target.stableOperationName,
                                    item.target.scope.name,
                                ).joinToString(":")
                            },
                        ) { item ->
                            BatchResultItem(item)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.action_dismiss))
                }
            },
        )
    }
}

@Composable
private fun BatchResultItem(item: BatchOperationItemResult) {
    val result = item.result
    ListItem(
        headlineContent = {
            Text(
                text = item.target.appLabel,
                fontWeight = FontWeight.Medium,
            )
        },
        supportingContent = {
            Column {
                Text(text = item.target.stableOperationName)
                Text(
                    text = when (result) {
                        is AppOpModeChangeResult.Success ->
                            stringResource(
                                R.string.batch_result_applied_mode,
                                modeLabel(result.appliedMode),
                            )

                        is AppOpModeChangeResult.Failure ->
                            stringResource(
                                R.string.batch_result_failed_phase,
                                phaseLabel(result.phase),
                            )
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        trailingContent = {
            Text(
                text = stringResource(
                    if (result is AppOpModeChangeResult.Success) {
                        R.string.batch_result_success
                    } else {
                        R.string.batch_result_failure
                    },
                ),
                color = if (result is AppOpModeChangeResult.Success) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                fontWeight = FontWeight.SemiBold,
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
        ),
    )
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
private fun phaseLabel(phase: AppOpModeChangePhase): String =
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
