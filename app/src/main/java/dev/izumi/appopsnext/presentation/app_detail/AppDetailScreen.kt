package dev.izumi.appopsnext.presentation.app_detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpsReadFailureReason
import dev.izumi.appopsnext.apps.model.InstalledApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    uiState: AppDetailUiState,
    modeChangeState: AppOpModeChangeUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onModeChangeRequested: (String, AppOpMode, AppOpMode) -> Unit,
    onModeChangeConfirmed: () -> Unit,
    onModeChangeDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = uiState.appOrNull()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = app?.label
                            ?: stringResource(R.string.app_detail_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(text = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { contentPadding ->
        when (uiState) {
            AppDetailUiState.Idle -> LoadingContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )

            is AppDetailUiState.WaitingForBackend -> MessageContent(
                app = uiState.app,
                message = stringResource(R.string.app_detail_waiting_backend),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )

            is AppDetailUiState.Loading -> LoadingContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )

            is AppDetailUiState.Ready -> ReadyContent(
                state = uiState,
                modeChangeState = modeChangeState,
                onModeChangeRequested = onModeChangeRequested,
                onModeChangeDismissed = onModeChangeDismissed,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )

            is AppDetailUiState.Failure -> FailureContent(
                app = uiState.app,
                reason = uiState.reason,
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )
        }
    }

    ModeChangeDialog(
        state = modeChangeState,
        onConfirm = onModeChangeConfirmed,
        onDismiss = onModeChangeDismissed,
    )
}

@Composable
private fun ReadyContent(
    state: AppDetailUiState.Ready,
    modeChangeState: AppOpModeChangeUiState,
    onModeChangeRequested: (String, AppOpMode, AppOpMode) -> Unit,
    onModeChangeDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isApplying = modeChangeState is AppOpModeChangeUiState.Applying
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 12.dp,
            end = 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            AppSummaryCard(
                app = state.app,
                operationCount = state.snapshot.entries.size,
            )
        }
        when (modeChangeState) {
            is AppOpModeChangeUiState.Success -> item {
                ModeChangeResultCard(
                    state = modeChangeState,
                    onDismiss = onModeChangeDismissed,
                )
            }

            is AppOpModeChangeUiState.Failure -> item {
                ModeChangeResultCard(
                    state = modeChangeState,
                    onDismiss = onModeChangeDismissed,
                )
            }

            else -> Unit
        }
        if (state.snapshot.entries.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.app_detail_no_operations),
                    modifier = Modifier.padding(vertical = 32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            itemsIndexed(
                items = state.snapshot.entries,
                key = { index, entry ->
                    "$index:${entry.hasUidModePrefix}:${entry.name}"
                },
            ) { _, entry ->
                AppOpListItem(
                    entry = entry,
                    editEnabled = !isApplying,
                    onModeSelected = { originalMode, requestedMode ->
                        onModeChangeRequested(
                            entry.name,
                            originalMode,
                            requestedMode,
                        )
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun AppSummaryCard(
    app: InstalledApp,
    operationCount: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    if (app.isSystemApp) {
                        R.string.app_type_system
                    } else {
                        R.string.app_type_user
                    },
                    app.uid,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    R.string.app_detail_operation_count,
                    operationCount,
                ),
                fontWeight = FontWeight.SemiBold,
            )
            // TODO(uid-mode-edit): Enable UID writes only after shared-UID
            // detection and impact confirmation are implemented.
            Text(
                text = stringResource(R.string.app_detail_uid_edit_unavailable),
                modifier = Modifier.padding(top = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MessageContent(
    app: InstalledApp,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = app.packageName,
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = message,
            modifier = Modifier.padding(top = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FailureContent(
    app: InstalledApp,
    reason: AppOpsReadFailureReason,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(
                when (reason) {
                    AppOpsReadFailureReason.BACKEND_UNAVAILABLE ->
                        R.string.status_appops_backend_unavailable_detail

                    AppOpsReadFailureReason.COMMAND_FAILED ->
                        R.string.status_appops_command_failed_detail

                    AppOpsReadFailureReason.COMMAND_TIMED_OUT ->
                        R.string.status_appops_command_timed_out_detail
                },
            ),
        )
        Text(
            text = app.packageName,
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(
            onClick = onRefresh,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(text = stringResource(R.string.action_retry))
        }
    }
}

private fun AppDetailUiState.appOrNull(): InstalledApp? =
    when (this) {
        AppDetailUiState.Idle -> null
        is AppDetailUiState.WaitingForBackend -> app
        is AppDetailUiState.Loading -> app
        is AppDetailUiState.Ready -> app
        is AppDetailUiState.Failure -> app
    }
