package dev.izumi.appopsnext.presentation.experimental

import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.presentation.components.MainPageChevron
import dev.izumi.appopsnext.presentation.components.MainPageEntryIcon
import dev.izumi.appopsnext.presentation.components.AppBottomSheet
import dev.izumi.appopsnext.monitor.MonitorSelfCheckResult
import dev.izumi.appopsnext.monitor.MonitorStatus
import dev.izumi.appopsnext.appops.parser.WatchRegistration

/**
 * A page of its own rather than a settings section, so later experiments have
 * somewhere to go without pushing the rest of the settings list down.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentalScreen(
    uiState: ExperimentalUiState,
    onBack: () -> Unit,
    onMonitorChange: (Boolean) -> Unit,
    onEnableUnconfirmedMonitor: () -> Unit,
    onOpenWatchers: () -> Unit,
    onOpenSettings: () -> Unit,
    logCount: Int,
    onOpenLog: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onDismissBatteryNotice: () -> Unit,
    onRefreshBatteryExemption: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var notificationsEnabled by remember { mutableStateOf(true) }
    var showAbout by remember { mutableStateOf(false) }
    // The exemption is granted in system settings, so it is re-read on every
    // return to this screen rather than once when it is first shown.
    LifecycleResumeEffect(Unit) {
        onRefreshBatteryExemption()
        notificationsEnabled = context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        onPauseOrDispose { }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.experimental_title),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ph_arrow_left),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            item {
                Text(
                    text = stringResource(R.string.experimental_caption),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                MonitorFeature(
                    uiState = uiState,
                    onMonitorChange = onMonitorChange,
                    onEnableUnconfirmedMonitor = onEnableUnconfirmedMonitor,
                    notificationsEnabled = notificationsEnabled,
                    onOpenNotificationSettings = {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                    },
                    onOpenBatterySettings = onOpenBatterySettings,
                    onDismissBatteryNotice = onDismissBatteryNotice,
                )
            }
            item {
                ExperimentalSectionTitle(stringResource(R.string.experimental_monitor_section))
                ExperimentalGroup {
                    val locked = uiState.monitorEnabled || uiState.monitorBusy
                    ExperimentalGroupRow(
                        modifier = if (locked) Modifier else Modifier.clickable(onClick = onOpenSettings),
                        leadingContent = { ExperimentalRowIcon(R.drawable.ic_ph_gear) },
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.monitor_settings_title),
                                color = if (locked) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                            )
                        },
                        supportingContent = {
                            val summary = if (uiState.targetCount == 0) {
                                stringResource(R.string.monitor_targets_empty)
                            } else {
                                stringResource(R.string.monitor_targets_summary, uiState.targetCount)
                            }
                            Text(
                                text = if (locked) stringResource(R.string.monitor_targets_locked, summary) else summary,
                            )
                        },
                        trailingContent = { if (!locked) MainPageChevron() },
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                    ExperimentalGroupRow(
                        modifier = Modifier.clickable(onClick = onOpenLog),
                        leadingContent = { ExperimentalRowIcon(R.drawable.ic_ph_clock_counter_clockwise) },
                        headlineContent = { Text(stringResource(R.string.monitor_log_title)) },
                        supportingContent = {
                            Text(
                                if (logCount > 0) {
                                    stringResource(R.string.monitor_log_summary, logCount)
                                } else {
                                    stringResource(R.string.monitor_log_summary_empty)
                                },
                            )
                        },
                        trailingContent = { MainPageChevron() },
                    )
                }
            }
            item {
                TextButton(
                    onClick = { showAbout = true },
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_ph_info),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(text = stringResource(R.string.monitor_about_title))
                }
            }
            item {
                ExperimentalSectionTitle(stringResource(R.string.experimental_diagnostics_section))
                ExperimentalGroup {
                    ExperimentalGroupRow(
                        modifier = Modifier.clickable(onClick = onOpenWatchers),
                        leadingContent = { ExperimentalRowIcon(R.drawable.ic_ph_user) },
                        headlineContent = { Text(stringResource(R.string.watchers_title)) },
                        supportingContent = { Text(stringResource(R.string.watchers_summary)) },
                        trailingContent = { MainPageChevron() },
                    )
                }
            }
        }
    }
    if (showAbout) {
        AppBottomSheet(
            onDismissRequest = { showAbout = false },
            title = { Text(stringResource(R.string.monitor_about_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.monitor_notification_required))
                    Text(stringResource(R.string.monitor_explainer))
                    Text(stringResource(R.string.monitor_event_count_note))
                }
            },
            confirmButton = {
                Button(onClick = { showAbout = false }) {
                    Text(stringResource(R.string.action_dismiss))
                }
            },
        )
    }
}

@Composable
private fun MonitorFeature(
    uiState: ExperimentalUiState,
    onMonitorChange: (Boolean) -> Unit,
    onEnableUnconfirmedMonitor: () -> Unit,
    notificationsEnabled: Boolean,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onDismissBatteryNotice: () -> Unit,
) {
    Column {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        ) {
            ListItem(
                leadingContent = { MainPageEntryIcon(R.drawable.ic_ph_eye) },
                headlineContent = { Text(text = stringResource(R.string.monitor_title)) },
                supportingContent = { Text(text = stringResource(R.string.monitor_summary)) },
                trailingContent = {
                    Switch(
                        checked = uiState.monitorEnabled,
                        enabled = !uiState.monitorBusy && uiState.targetCount > 0,
                        onCheckedChange = onMonitorChange,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        Text(
            text = stringResource(R.string.monitor_notification_summary),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (notificationsEnabled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error,
        )
        if (!notificationsEnabled) {
            TextButton(onClick = onOpenNotificationSettings, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.monitor_open_notification_settings))
            }
        }
        MonitorStatusText(uiState)
        if (!uiState.monitorEnabled && !uiState.monitorBusy &&
            uiState.selfCheckResult is MonitorSelfCheckResult.Unconfirmed
        ) {
            TextButton(
                onClick = onEnableUnconfirmedMonitor,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) { Text(stringResource(R.string.monitor_enable_unconfirmed)) }
        }
        // Vendors remap the exemption intent to their own per-app battery page,
        // where granting it does not put the app on the platform whitelist this
        // reads. The notice can therefore be true forever on such a device, so
        // it can be dismissed by hand as well as resolving itself.
        if (uiState.showBatteryNotice) {
            ExperimentalListCard(
                modifier = Modifier.clickable(onClick = onOpenBatterySettings),
                leadingContent = {
                    MainPageEntryIcon(
                        iconRes = R.drawable.ic_ph_warning_circle,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.error,
                    )
                },
                headlineContent = {
                    Text(
                        text = stringResource(R.string.monitor_battery_title),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                supportingContent = {
                    Text(text = stringResource(R.string.monitor_battery_summary))
                },
                trailingContent = {
                    IconButton(onClick = onDismissBatteryNotice) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ph_x),
                            contentDescription = stringResource(
                                R.string.monitor_battery_dismiss,
                            ),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun ExperimentalSectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MonitorStatusText(uiState: ExperimentalUiState) {
    val message: String?
    val isError: Boolean
    when {
        uiState.monitorBusy -> {
            message = stringResource(R.string.monitor_checking)
            isError = false
        }

        uiState.targetCount == 0 && !uiState.monitorEnabled -> {
            message = stringResource(R.string.monitor_needs_targets)
            isError = false
        }

        // The switch alone cannot show whether the watches are live; a restore
        // that failed leaves it on with nothing registered.
        uiState.monitorEnabled && uiState.status == null -> {
            message = uiState.monitorFailure?.let {
                stringResource(R.string.monitor_stopped_reason, it)
            } ?: stringResource(R.string.monitor_not_running)
            isError = true
        }

        uiState.status?.callbackFailure != null -> {
            message = stringResource(R.string.monitor_callback_failed, uiState.status.callbackFailure)
            isError = true
        }

        uiState.monitorEnabled && uiState.status?.registry != WatchRegistration.CONFIRMED -> {
            message = stringResource(R.string.monitor_registry_unconfirmed_running)
            isError = true
        }

        uiState.monitorEnabled && uiState.status != null -> {
            message = partialWatchMessage(uiState.status)
                ?: stringResource(R.string.monitor_check_passed)
            isError = uiState.status?.partial == true
        }

        uiState.selfCheckResult is MonitorSelfCheckResult.Unconfirmed -> {
            message = stringResource(R.string.monitor_check_unconfirmed)
            isError = true
        }

        uiState.selfCheckResult is MonitorSelfCheckResult.Failed -> {
            message = stringResource(
                R.string.monitor_check_failed,
                (uiState.selfCheckResult as MonitorSelfCheckResult.Failed).reason,
            )
            isError = true
        }

        else -> {
            message = null
            isError = false
        }
    }
    if (message == null) return
    Text(
        text = message,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        },
    )
}

/** Names the watches that did not register, instead of claiming full coverage. */
@Composable
private fun partialWatchMessage(status: MonitorStatus?): String? {
    if (status == null || !status.partial) return null
    val missing = buildList {
        if (!status.activeWatch) add(stringResource(R.string.monitor_watch_active))
        if (!status.notedWatch) add(stringResource(R.string.monitor_watch_noted))
        if (!status.startedWatch) add(stringResource(R.string.monitor_watch_started))
    }
    if (missing.isEmpty()) return null
    return stringResource(R.string.monitor_partial_watch, missing.joinToString(stringResource(R.string.list_separator)))
}
