package dev.izumi.appopsnext.presentation.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.appops.model.AppOpsReadFailureReason
import dev.izumi.appopsnext.appops.model.AppOpsReadState
import dev.izumi.appopsnext.shizuku.model.PrivilegedServiceState
import dev.izumi.appopsnext.shizuku.model.PrivilegedServiceFailureReason
import dev.izumi.appopsnext.shizuku.model.ShizukuFailureReason
import dev.izumi.appopsnext.shizuku.model.ShizukuState

@Composable
fun DiagnosticsSection(
    uiState: DiagnosticsUiState,
    onShizukuAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        DiagnosticStatusItem(
            title = stringResource(R.string.status_device_title),
            value = stringResource(
                R.string.status_device_value,
                uiState.device.manufacturer,
                uiState.device.model,
            ),
            detail = stringResource(
                R.string.status_device_detail,
                uiState.device.androidVersion,
                uiState.device.apiLevel,
            ),
            level = DiagnosticStatusLevel.NEUTRAL,
        )
        HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
        ShizukuStatusCard(
            state = uiState.shizukuState,
            onAction = onShizukuAction,
        )
        HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
        PrivilegedServiceStatusCard(
            serviceState = uiState.privilegedServiceState,
            readState = uiState.appOpsReadState,
        )
    }
}

@Composable
private fun ShizukuStatusCard(
    state: ShizukuState,
    onAction: () -> Unit,
) {
    val presentation = when (state) {
        ShizukuState.Checking -> StatusPresentation(
            value = stringResource(R.string.status_checking),
            detail = stringResource(R.string.status_shizuku_checking_detail),
            level = DiagnosticStatusLevel.NEUTRAL,
        )

        is ShizukuState.Unavailable -> StatusPresentation(
            value = stringResource(
                if (state.isInstalled) {
                    R.string.status_shizuku_not_running
                } else {
                    R.string.status_shizuku_not_installed
                },
            ),
            detail = stringResource(R.string.status_shizuku_unavailable_detail),
            actionLabel = stringResource(R.string.action_retry),
            level = DiagnosticStatusLevel.ERROR,
        )

        ShizukuState.Unsupported -> StatusPresentation(
            value = stringResource(R.string.status_unsupported),
            detail = stringResource(R.string.status_shizuku_unsupported_detail),
            level = DiagnosticStatusLevel.ERROR,
        )

        ShizukuState.PermissionRequired -> StatusPresentation(
            value = stringResource(R.string.status_permission_required),
            detail = stringResource(R.string.status_shizuku_permission_detail),
            actionLabel = stringResource(R.string.action_grant_shizuku),
            level = DiagnosticStatusLevel.WARNING,
        )

        ShizukuState.PermissionDenied -> StatusPresentation(
            value = stringResource(R.string.status_permission_denied),
            detail = stringResource(R.string.status_shizuku_denied_detail),
            actionLabel = stringResource(R.string.action_request_again),
            level = DiagnosticStatusLevel.ERROR,
        )

        is ShizukuState.Ready -> StatusPresentation(
            value = stringResource(R.string.status_ready),
            detail = stringResource(
                R.string.status_shizuku_ready_detail,
                state.serverVersion,
                state.serverUid,
            ),
            level = DiagnosticStatusLevel.SUCCESS,
        )

        is ShizukuState.Failure -> StatusPresentation(
            value = stringResource(R.string.status_error),
            detail = stringResource(
                when (state.reason) {
                    ShizukuFailureReason.STATE_READ_FAILED ->
                        R.string.status_shizuku_read_failed_detail

                    ShizukuFailureReason.PERMISSION_REQUEST_FAILED ->
                        R.string.status_shizuku_permission_failed_detail
                },
            ),
            actionLabel = stringResource(R.string.action_retry),
            level = DiagnosticStatusLevel.ERROR,
        )
    }

    DiagnosticStatusItem(
        title = stringResource(R.string.status_shizuku_title),
        value = presentation.value,
        detail = presentation.detail,
        level = presentation.level,
        actionLabel = presentation.actionLabel,
        onAction = presentation.actionLabel?.let { onAction },
    )
}

@Composable
private fun PrivilegedServiceStatusCard(
    serviceState: PrivilegedServiceState,
    readState: AppOpsReadState,
) {
    val presentation = when (serviceState) {
        PrivilegedServiceState.Disconnected -> StatusPresentation(
            value = stringResource(R.string.status_disconnected),
            detail = stringResource(R.string.status_appops_disconnected_detail),
            level = DiagnosticStatusLevel.WARNING,
        )

        PrivilegedServiceState.Connecting -> StatusPresentation(
            value = stringResource(R.string.status_connecting),
            detail = stringResource(R.string.status_user_service_connecting_detail),
            level = DiagnosticStatusLevel.NEUTRAL,
        )

        is PrivilegedServiceState.Connected ->
            appOpsReadPresentation(serviceState, readState)

        is PrivilegedServiceState.Failure -> StatusPresentation(
            value = stringResource(R.string.status_error),
            detail = stringResource(
                when (serviceState.reason) {
                    PrivilegedServiceFailureReason.EMPTY_BINDER ->
                        R.string.status_user_service_empty_binder_detail

                    PrivilegedServiceFailureReason.INITIALIZATION_FAILED ->
                        R.string.status_user_service_initialization_failed_detail

                    PrivilegedServiceFailureReason.BIND_FAILED ->
                        R.string.status_user_service_bind_failed_detail
                },
            ),
            level = DiagnosticStatusLevel.ERROR,
        )
    }

    DiagnosticStatusItem(
        title = stringResource(R.string.status_appops_title),
        value = presentation.value,
        detail = presentation.detail,
        level = presentation.level,
    )
}

@Composable
private fun appOpsReadPresentation(
    serviceState: PrivilegedServiceState.Connected,
    readState: AppOpsReadState,
): StatusPresentation =
    when (readState) {
        AppOpsReadState.WaitingForBackend,
        AppOpsReadState.Reading,
        -> StatusPresentation(
            value = stringResource(R.string.status_appops_reading),
            detail = stringResource(
                R.string.status_user_service_connected_detail,
                serviceState.info.uid,
                serviceState.info.pid,
                serviceState.info.apiLevel,
            ),
            level = DiagnosticStatusLevel.NEUTRAL,
        )

        is AppOpsReadState.Ready -> StatusPresentation(
            value = stringResource(R.string.status_appops_read_success),
            detail = stringResource(
                R.string.status_appops_read_success_detail,
                readState.operationCount,
                serviceState.info.uid,
            ),
            level = DiagnosticStatusLevel.SUCCESS,
        )

        is AppOpsReadState.Failure -> StatusPresentation(
            value = stringResource(R.string.status_appops_read_failed),
            detail = stringResource(
                when (readState.reason) {
                    AppOpsReadFailureReason.BACKEND_UNAVAILABLE ->
                        R.string.status_appops_backend_unavailable_detail

                    AppOpsReadFailureReason.COMMAND_FAILED ->
                        R.string.status_appops_command_failed_detail

                    AppOpsReadFailureReason.COMMAND_TIMED_OUT ->
                        R.string.status_appops_command_timed_out_detail
                },
            ),
            level = DiagnosticStatusLevel.ERROR,
        )
    }

private data class StatusPresentation(
    val value: String,
    val detail: String,
    val actionLabel: String? = null,
    val level: DiagnosticStatusLevel,
)

@Composable
private fun DiagnosticStatusItem(
    title: String,
    value: String,
    detail: String,
    level: DiagnosticStatusLevel,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val statusColor = when (level) {
        DiagnosticStatusLevel.NEUTRAL ->
            MaterialTheme.colorScheme.outline

        DiagnosticStatusLevel.SUCCESS ->
            MaterialTheme.colorScheme.tertiary

        DiagnosticStatusLevel.WARNING ->
            MaterialTheme.colorScheme.secondary

        DiagnosticStatusLevel.ERROR ->
            MaterialTheme.colorScheme.error
    }
    ListItem(
        modifier = modifier.fillMaxWidth(),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor),
            )
        },
        headlineContent = {
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
            )
        },
        supportingContent = {
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = value,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(text = detail)
                if (actionLabel != null && onAction != null) {
                    TextButton(
                        onClick = onAction,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(text = actionLabel)
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
        ),
    )
}

private enum class DiagnosticStatusLevel {
    NEUTRAL,
    SUCCESS,
    WARNING,
    ERROR,
}
