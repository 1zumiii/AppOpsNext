package dev.izumi.appopsnext.presentation.notifications

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.izumi.appopsnext.AppOpsNextApplication
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.appops.changes.AppOpChangeDrift
import dev.izumi.appopsnext.appops.model.AppOpNames
import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.batch.model.BatchOperationReport
import dev.izumi.appopsnext.batch.model.BatchOperationTarget
import dev.izumi.appopsnext.newapps.NewAppResult
import dev.izumi.appopsnext.presentation.app_detail.AppOpChangeStatusText
import dev.izumi.appopsnext.presentation.app_detail.AppOpDisplayCatalog
import dev.izumi.appopsnext.presentation.app_detail.modeLabel
import dev.izumi.appopsnext.presentation.components.AppIcon
import dev.izumi.appopsnext.presentation.experimental.ExperimentalGroup
import dev.izumi.appopsnext.presentation.experimental.ExperimentalGroupRow
import dev.izumi.appopsnext.presentation.experimental.ExperimentalSectionTitle
import dev.izumi.appopsnext.presentation.experimental.GuardEventRow
import kotlinx.coroutines.launch

/**
 * What the app found while the user was elsewhere: settings it changed that
 * were changed again since, and what the guard did about its rules.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    busy: Boolean,
    onBack: () -> Unit,
    onAppSelected: (InstalledApp) -> Unit,
    onRestoreRequested: (title: String, targets: List<BatchOperationTarget>) -> Unit,
    onReportRequested: (BatchOperationReport) -> Unit,
    modifier: Modifier = Modifier,
) {
    val application = LocalContext.current.applicationContext as AppOpsNextApplication
    val drifts by application.appOpChangeDriftMonitor.drifts.collectAsStateWithLifecycle()
    val guardEvents by application.guardRepository.events.collectAsStateWithLifecycle(emptyList())
    val newAppResults by application.newAppPolicyCoordinator.results.collectAsStateWithLifecycle(emptyList())
    val changeLog = application.appOpChangeLog
    val currentUserId = application.userAppsRepository.currentUserId
    val scope = rememberCoroutineScope()
    val restoreTitle = stringResource(R.string.notifications_restore_title)
    val restorable = drifts.filter { it.status.currentMode != null }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.notifications_title), fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ph_arrow_left),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (drifts.isEmpty() && guardEvents.isEmpty() && newAppResults.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.notifications_empty),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (drifts.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            ExperimentalSectionTitle(stringResource(R.string.notifications_changed_section))
                        }
                        if (restorable.size > 1) {
                            TextButton(
                                onClick = { onRestoreRequested(restoreTitle, restorable.map { it.restoreTarget() }) },
                                enabled = !busy,
                            ) {
                                Text(stringResource(R.string.notifications_restore_all))
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.notifications_changed_summary),
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                items(
                    items = drifts,
                    key = { drift ->
                        val record = drift.status.record
                        "${record.userId}/${record.packageName}/${record.stableOperationName}"
                    },
                ) { drift ->
                    ChangedSettingCard(
                        drift = drift,
                        showUser = drift.status.record.userId != currentUserId,
                        busy = busy,
                        onOpen = { onAppSelected(drift.app) },
                        onRestore = { onRestoreRequested(restoreTitle, listOf(drift.restoreTarget())) },
                        onKeep = {
                            scope.launch {
                                changeLog.forget(
                                    drift.app.userId,
                                    drift.app.packageName,
                                    drift.status.record.stableOperationName,
                                )
                            }
                        },
                    )
                }
            }
            if (newAppResults.isNotEmpty()) {
                item {
                    ExperimentalSectionTitle(stringResource(R.string.new_app_policy_notification_channel))
                    ExperimentalGroup {
                        newAppResults.take(MAX_NEW_APP_RESULTS).forEachIndexed { index, result ->
                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            NewAppResultRow(
                                result = result,
                                onOpen = { onReportRequested(BatchOperationReport(result.appLabel, result.items)) },
                            )
                        }
                    }
                }
            }
            if (guardEvents.isNotEmpty()) {
                item {
                    ExperimentalSectionTitle(stringResource(R.string.notifications_guard_section))
                    ExperimentalGroup {
                        guardEvents.take(MAX_GUARD_EVENTS).forEachIndexed { index, event ->
                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            GuardEventRow(event = event, notifier = application.guardNotifier)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangedSettingCard(
    drift: AppOpChangeDrift,
    showUser: Boolean,
    busy: Boolean,
    onOpen: () -> Unit,
    onRestore: () -> Unit,
    onKeep: () -> Unit,
) {
    val record = drift.status.record
    val shellName = AppOpNames.shellName(record.stableOperationName)
    val operationLabel = AppOpDisplayCatalog.labelResOf(shellName)?.let { stringResource(it) } ?: shellName
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.clickable(onClick = onOpen).padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(
                    packageName = drift.app.packageName,
                    appLabel = drift.app.label,
                    size = 40.dp,
                    userId = drift.app.userId,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = drift.app.label,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (showUser) {
                            stringResource(
                                R.string.notifications_operation_in_user,
                                operationLabel,
                                record.userId,
                            )
                        } else {
                            operationLabel
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            AppOpChangeStatusText(
                status = drift.status,
                modifier = Modifier.padding(top = 12.dp),
            )
            drift.status.currentMode?.let { current ->
                Text(
                    text = stringResource(R.string.notifications_current_mode, modeLabel(current)),
                    modifier = Modifier.padding(top = 2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (drift.status.currentMode != null) {
                    OutlinedButton(onClick = onRestore, enabled = !busy) {
                        Text(stringResource(R.string.app_op_change_restore))
                    }
                }
                TextButton(onClick = onKeep, enabled = !busy) {
                    Text(stringResource(R.string.app_op_change_keep))
                }
            }
        }
    }
}

@Composable
private fun NewAppResultRow(result: NewAppResult, onOpen: () -> Unit) {
    ExperimentalGroupRow(
        modifier = Modifier.clickable(onClick = onOpen),
        leadingContent = {
            AppIcon(
                packageName = result.installation.packageName,
                appLabel = result.appLabel,
                size = 32.dp,
            )
        },
        headlineContent = { Text(result.appLabel) },
        supportingContent = {
            Text(
                text = stringResource(
                    R.string.new_app_policy_notification_result,
                    result.successCount,
                    result.failureCount,
                ),
                color = if (result.failureCount > 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        trailingContent = {
            Text(
                text = DateUtils.getRelativeTimeSpanString(
                    result.installation.firstInstallTimeMillis,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE,
                ).toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

/** Writes back the mode this app last set, at the scope it set it. */
private fun AppOpChangeDrift.restoreTarget() = BatchOperationTarget(
    packageName = app.packageName,
    appLabel = app.label,
    uid = app.uid,
    stableOperationName = status.record.stableOperationName,
    preferredScope = status.record.scope,
    requestedMode = status.record.recordedMode,
    userId = app.userId,
)

private const val MAX_GUARD_EVENTS = 20
private const val MAX_NEW_APP_RESULTS = 10
