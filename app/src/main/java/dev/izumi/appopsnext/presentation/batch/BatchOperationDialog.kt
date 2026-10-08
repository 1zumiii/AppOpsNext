package dev.izumi.appopsnext.presentation.batch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.presentation.components.AppBottomSheet
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpModeChangePhase
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.appops.model.AppOpsRestorationStatus
import dev.izumi.appopsnext.batch.model.BatchOperationItemResult
import dev.izumi.appopsnext.presentation.app_detail.AppOpDisplayCatalog

@Composable
fun BatchOperationDialog(
    state: BatchOperationUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        BatchOperationUiState.Idle -> Unit

        is BatchOperationUiState.Confirming -> AppBottomSheet(
            onDismissRequest = onDismiss,
            title = {
                Text(text = stringResource(R.string.batch_confirm_title))
            },
            text = {
                var expanded by rememberSaveable(state.request) {
                    mutableStateOf(state.request.previewTargets.size <= 5)
                }
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
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
                        if (state.request.affectedPackages.isNotEmpty()) {
                            Text(
                                text = stringResource(
                                    R.string.permission_uid_affected,
                                    state.request.affectedPackages.joinToString("\n"),
                                ),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    item {
                        state.request.previewTargets.map { it.requestedMode }.distinct()
                            .singleOrNull()?.let { mode ->
                                Text(stringResource(R.string.batch_requested_mode, modeLabel(mode)))
                            }
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(stringResource(if (expanded) R.string.batch_hide_targets else R.string.batch_show_targets))
                        }
                    }
                    if (expanded) {
                        items(state.request.previewTargets) { target ->
                            ListItem(
                                headlineContent = { Text(target.appLabel) },
                                supportingContent = {
                                    Column {
                                        Text(target.packageName)
                                        Text(operationLabel(target.stableOperationName))
                                    }
                                },
                                trailingContent = { Text(modeLabel(target.requestedMode)) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = onConfirm) {
                    Text(text = stringResource(R.string.action_apply))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )

        is BatchOperationUiState.Running -> AppBottomSheet(
            onDismissRequest = {},
            dismissible = false,
            showActions = false,
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

        is BatchOperationUiState.Finished -> AppBottomSheet(
            onDismissRequest = onDismiss,
            title = {
                Text(text = stringResource(R.string.batch_result_title))
            },
            text = {
                var failuresOnly by rememberSaveable(state.report) { mutableStateOf(false) }
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
                    FilterChip(
                        selected = failuresOnly,
                        onClick = { failuresOnly = !failuresOnly },
                        label = { Text(stringResource(R.string.batch_failures_only)) },
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                    ) {
                        items(
                            items = state.report.results.filter {
                                !failuresOnly || it.result is AppOpModeChangeResult.Failure
                            },
                            key = { item ->
                                listOf(
                                    item.target.packageName,
                                    item.target.stableOperationName,
                                    item.target.preferredScope.name,
                                ).joinToString(":")
                            },
                        ) { item ->
                            BatchResultItem(item)
                        }
                        if (failuresOnly && state.report.failureCount == 0) {
                            item { Text(stringResource(R.string.batch_no_failures)) }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = onDismiss) {
                    Text(text = stringResource(R.string.action_dismiss))
                }
            },
        )
    }
}

@Composable
private fun operationLabel(operation: String): String =
    AppOpDisplayCatalog.labelResOf(operation)?.let { stringResource(it) } ?: operation

@Composable
private fun BatchResultItem(item: BatchOperationItemResult) {
    val result = item.result
    var details by rememberSaveable(item.target) { mutableStateOf(false) }
    ListItem(
        headlineContent = {
            Text(
                text = item.target.appLabel,
                fontWeight = FontWeight.Medium,
            )
        },
        supportingContent = {
            Column {
                Text(text = operationLabel(item.target.stableOperationName))
                Text(
                    text = when (result) {
                        is AppOpModeChangeResult.Success ->
                            stringResource(
                                R.string.batch_result_applied_mode,
                                modeLabel(result.appliedMode),
                            )

                        is AppOpModeChangeResult.Failure ->
                            failureExplanation(result)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (result is AppOpModeChangeResult.Failure) {
                    TextButton(onClick = { details = !details }) {
                        Text(stringResource(R.string.batch_failure_details))
                    }
                    if (details) {
                        Text(item.target.packageName)
                        Text(item.target.stableOperationName)
                        Text(stringResource(R.string.batch_result_failed_phase, phaseLabel(result.phase)))
                        Text(stringResource(when (result.restorationStatus) {
                            AppOpsRestorationStatus.NOT_REQUIRED -> R.string.app_detail_mode_restore_not_required
                            AppOpsRestorationStatus.SUCCEEDED -> R.string.app_detail_mode_restored
                            AppOpsRestorationStatus.FAILED -> R.string.app_detail_mode_restore_failed
                        }))
                    }
                }
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
private fun failureExplanation(result: AppOpModeChangeResult.Failure): String = stringResource(
    if (result.restorationStatus == AppOpsRestorationStatus.FAILED) {
        R.string.app_detail_mode_restore_failed
    } else when (result.phase) {
        AppOpModeChangePhase.READ_ORIGINAL -> R.string.batch_failure_read
        AppOpModeChangePhase.CHECK_ORIGINAL -> R.string.batch_failure_changed
        AppOpModeChangePhase.APPLY_REQUESTED -> R.string.batch_failure_write
        AppOpModeChangePhase.VERIFY_REQUESTED -> R.string.batch_failure_verify
        AppOpModeChangePhase.RESTORE_ORIGINAL,
        AppOpModeChangePhase.VERIFY_RESTORED -> R.string.app_detail_mode_restore_failed
    },
)

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
