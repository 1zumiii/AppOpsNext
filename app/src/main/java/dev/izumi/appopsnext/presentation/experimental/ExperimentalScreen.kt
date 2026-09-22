package dev.izumi.appopsnext.presentation.experimental

import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
    onOpenBatterySettings: () -> Unit,
    onDismissBatteryNotice: () -> Unit,
    onRefreshBatteryExemption: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var notificationsEnabled by remember { mutableStateOf(true) }
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
                            painter = painterResource(R.drawable.ic_arrow_back),
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
            item { HorizontalDivider() }
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
                    onOpenSettings = onOpenSettings,
                    onOpenBatterySettings = onOpenBatterySettings,
                    onDismissBatteryNotice = onDismissBatteryNotice,
                )
            }
            item { HorizontalDivider() }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenWatchers),
                    headlineContent = { Text(stringResource(R.string.watchers_title)) },
                    supportingContent = { Text(stringResource(R.string.watchers_summary)) },
                )
            }
        }
    }
}

@Composable
private fun MonitorFeature(
    uiState: ExperimentalUiState,
    onMonitorChange: (Boolean) -> Unit,
    onEnableUnconfirmedMonitor: () -> Unit,
    notificationsEnabled: Boolean,
    onOpenNotificationSettings: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onDismissBatteryNotice: () -> Unit,
) {
    Column {
        ListItem(
            headlineContent = { Text(text = stringResource(R.string.monitor_title)) },
            supportingContent = { Text(text = stringResource(R.string.monitor_summary)) },
            trailingContent = {
                Switch(
                    checked = uiState.monitorEnabled,
                    enabled = !uiState.monitorBusy && uiState.targetCount > 0,
                    onCheckedChange = onMonitorChange,
                )
            },
        )
        Text(
            text = stringResource(R.string.monitor_notification_required),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (notificationsEnabled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error,
        )
        if (!notificationsEnabled) {
            TextButton(onClick = onOpenNotificationSettings, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.monitor_open_notification_settings))
            }
        }
        // One entry is closed while the monitor runs instead of every setting
        // inside it, so the reason has to be stated once.
        val locked = uiState.monitorEnabled || uiState.monitorBusy
        ListItem(
            modifier = if (locked) {
                Modifier
            } else {
                Modifier.clickable(onClick = onOpenSettings)
            },
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.ic_action_manage),
                    contentDescription = null,
                    tint = if (locked) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            },
            headlineContent = {
                Text(
                    text = stringResource(R.string.monitor_settings_title),
                    color = if (locked) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        Color.Unspecified
                    },
                )
            },
            supportingContent = {
                val summary = if (uiState.targetCount == 0) {
                    stringResource(R.string.monitor_targets_empty)
                } else {
                    stringResource(
                        R.string.monitor_targets_summary,
                        uiState.targetCount,
                    )
                }
                Text(
                    text = if (locked) {
                        stringResource(R.string.monitor_targets_locked, summary)
                    } else {
                        summary
                    },
                )
            },
        )
        Text(
            text = stringResource(R.string.monitor_explainer) + "\n\n" +
                stringResource(R.string.monitor_event_count_note),
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            ListItem(
                modifier = Modifier.clickable(onClick = onOpenBatterySettings),
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
                            painter = painterResource(R.drawable.ic_action_close),
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
    return stringResource(R.string.monitor_partial_watch, missing.joinToString("、"))
}
