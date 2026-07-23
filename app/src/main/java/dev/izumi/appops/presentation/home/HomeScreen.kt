package dev.izumi.appops.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.izumi.appops.BuildConfig
import dev.izumi.appops.R
import dev.izumi.appops.appops.model.AppOpsReadFailureReason
import dev.izumi.appops.appops.model.AppOpsReadState
import dev.izumi.appops.appops.model.AppOpsRestorationStatus
import dev.izumi.appops.appops.model.AppOpsWriteTestPhase
import dev.izumi.appops.appops.model.AppOpsWriteTestState
import dev.izumi.appops.presentation.components.StatusCard
import dev.izumi.appops.shizuku.model.PrivilegedServiceState
import dev.izumi.appops.shizuku.model.PrivilegedServiceFailureReason
import dev.izumi.appops.shizuku.model.ShizukuFailureReason
import dev.izumi.appops.shizuku.model.ShizukuState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onShizukuAction: () -> Unit,
    onAppOpsWriteTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.home_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(4.dp))
            StatusCard(
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
            )
            ShizukuStatusCard(
                state = uiState.shizukuState,
                onAction = onShizukuAction,
            )
            PrivilegedServiceStatusCard(
                serviceState = uiState.privilegedServiceState,
                readState = uiState.appOpsReadState,
            )
            // TODO(remove-debug-write-test): Remove this temporary card after
            // the production permission-detail write flow covers the same path.
            if (BuildConfig.DEBUG) {
                AppOpsWriteTestCard(
                    state = uiState.appOpsWriteTestState,
                    isTestTargetInstalled = uiState.isTestTargetInstalled,
                    isBackendConnected =
                        uiState.privilegedServiceState is
                            PrivilegedServiceState.Connected,
                    onAction = onAppOpsWriteTest,
                )
            }
        }
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
        )

        ShizukuState.Unsupported -> StatusPresentation(
            value = stringResource(R.string.status_unsupported),
            detail = stringResource(R.string.status_shizuku_unsupported_detail),
        )

        ShizukuState.PermissionRequired -> StatusPresentation(
            value = stringResource(R.string.status_permission_required),
            detail = stringResource(R.string.status_shizuku_permission_detail),
            actionLabel = stringResource(R.string.action_grant_shizuku),
        )

        ShizukuState.PermissionDenied -> StatusPresentation(
            value = stringResource(R.string.status_permission_denied),
            detail = stringResource(R.string.status_shizuku_denied_detail),
            actionLabel = stringResource(R.string.action_request_again),
        )

        is ShizukuState.Ready -> StatusPresentation(
            value = stringResource(R.string.status_ready),
            detail = stringResource(
                R.string.status_shizuku_ready_detail,
                state.serverVersion,
                state.serverUid,
            ),
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
        )
    }

    StatusCard(
        title = stringResource(R.string.status_shizuku_title),
        value = presentation.value,
        detail = presentation.detail,
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
        )

        PrivilegedServiceState.Connecting -> StatusPresentation(
            value = stringResource(R.string.status_connecting),
            detail = stringResource(R.string.status_user_service_connecting_detail),
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
        )
    }

    StatusCard(
        title = stringResource(R.string.status_appops_title),
        value = presentation.value,
        detail = presentation.detail,
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
        )

        is AppOpsReadState.Ready -> StatusPresentation(
            value = stringResource(R.string.status_appops_read_success),
            detail = stringResource(
                R.string.status_appops_read_success_detail,
                readState.operationCount,
                serviceState.info.uid,
            ),
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
        )
    }

@Composable
private fun AppOpsWriteTestCard(
    state: AppOpsWriteTestState,
    isTestTargetInstalled: Boolean,
    isBackendConnected: Boolean,
    onAction: () -> Unit,
) {
    val presentation = when {
        !isTestTargetInstalled -> StatusPresentation(
            value = stringResource(R.string.status_write_test_target_missing),
            detail = stringResource(R.string.status_write_test_target_missing_detail),
            actionLabel = stringResource(R.string.action_check_test_target),
        )

        state is AppOpsWriteTestState.NotRun -> StatusPresentation(
            value = stringResource(R.string.status_write_test_not_run),
            detail = stringResource(R.string.status_write_test_not_run_detail),
            actionLabel = stringResource(R.string.action_run_write_test),
        )

        state is AppOpsWriteTestState.Running -> StatusPresentation(
            value = stringResource(R.string.status_write_test_running),
            detail = stringResource(R.string.status_write_test_running_detail),
        )

        state is AppOpsWriteTestState.Success -> StatusPresentation(
            value = stringResource(R.string.status_write_test_success),
            detail = stringResource(
                R.string.status_write_test_success_detail,
                state.originalMode.shellValue,
                state.testMode.shellValue,
                state.restoredMode.shellValue,
            ),
            actionLabel = stringResource(R.string.action_run_again),
        )

        state is AppOpsWriteTestState.Failure -> StatusPresentation(
            value = stringResource(R.string.status_write_test_failed),
            detail = stringResource(
                R.string.status_write_test_failed_detail,
                writeTestPhaseLabel(state.phase),
                restorationStatusLabel(state.restorationStatus),
            ),
            actionLabel = stringResource(R.string.action_run_again),
        )

        else -> error("Unhandled AppOps write-test state")
    }

    StatusCard(
        title = stringResource(R.string.status_write_test_title),
        value = presentation.value,
        detail = presentation.detail,
        actionLabel = presentation.actionLabel,
        onAction = if (isBackendConnected) onAction else null,
    )
}

@Composable
private fun writeTestPhaseLabel(phase: AppOpsWriteTestPhase): String =
    stringResource(
        when (phase) {
            AppOpsWriteTestPhase.READ_ORIGINAL ->
                R.string.status_write_test_phase_read_original

            AppOpsWriteTestPhase.APPLY_TEST_MODE ->
                R.string.status_write_test_phase_apply

            AppOpsWriteTestPhase.VERIFY_TEST_MODE ->
                R.string.status_write_test_phase_verify

            AppOpsWriteTestPhase.RESTORE_ORIGINAL ->
                R.string.status_write_test_phase_restore

            AppOpsWriteTestPhase.VERIFY_RESTORED ->
                R.string.status_write_test_phase_verify_restored
        },
    )

@Composable
private fun restorationStatusLabel(status: AppOpsRestorationStatus): String =
    stringResource(
        when (status) {
            AppOpsRestorationStatus.NOT_REQUIRED ->
                R.string.status_write_test_restore_not_required

            AppOpsRestorationStatus.SUCCEEDED ->
                R.string.status_write_test_restored

            AppOpsRestorationStatus.FAILED ->
                R.string.status_write_test_restore_failed
        },
    )

private data class StatusPresentation(
    val value: String,
    val detail: String,
    val actionLabel: String? = null,
)
