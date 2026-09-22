package dev.izumi.appopsnext.presentation.experimental

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.izumi.appopsnext.R
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchersScreen(uiState: WatchersUiState, onRefresh: () -> Unit, onBack: () -> Unit) {
    LifecycleResumeEffect(Unit) {
        onRefresh()
        onPauseOrDispose { }
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.watchers_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                }
            },
            actions = {
                TextButton(onClick = onRefresh, enabled = !uiState.loading) {
                    Text(stringResource(R.string.watchers_refresh))
                }
            },
        )
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(stringResource(R.string.watchers_explainer), Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium)
                if (uiState.loading) LinearProgressIndicator()
                if (uiState.unavailable || uiState.incomplete) {
                    Text(
                        stringResource(if (uiState.unavailable) R.string.watchers_unavailable else R.string.watchers_incomplete),
                        Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error,
                    )
                }
                uiState.capturedAtMillis?.let {
                    Text(
                        stringResource(R.string.watchers_snapshot_time, DateFormat.getDateTimeInstance().format(Date(it))),
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (uiState.owners.isEmpty() && !uiState.incomplete) {
                        Text(stringResource(R.string.watchers_empty), Modifier.padding(16.dp))
                    }
                }
            }
            items(uiState.owners, key = { "${it.uid}:${it.pid}" }) { owner ->
                WatcherOwnerRow(owner)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun WatcherOwnerRow(owner: WatcherOwner) {
    var expanded by rememberSaveable(owner.uid, owner.pid) { mutableStateOf(false) }
    Column {
        ListItem(
            modifier = Modifier.clickable { expanded = !expanded },
            headlineContent = {
                Text(owner.apps.singleOrNull()?.label ?: stringResource(R.string.watchers_uid, owner.uid))
            },
            supportingContent = {
                Text(stringResource(R.string.watchers_owner_summary, owner.uid, owner.pid, owner.registrations.size))
            },
            trailingContent = { Text(stringResource(if (expanded) R.string.watchers_collapse else R.string.watchers_expand)) },
        )
        if (expanded) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                Text(
                    if (owner.apps.isEmpty()) stringResource(R.string.watchers_owner_unresolved)
                    else stringResource(R.string.watchers_owner_packages, owner.apps.joinToString("\n") { "${it.label} (${it.packageName})" }),
                    style = MaterialTheme.typography.bodySmall,
                )
                owner.registrations.forEach { row ->
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    Text(stringResource(R.string.watchers_reported_op, row.reportedOperation), style = MaterialTheme.typography.titleSmall)
                    if (row.operations.isNotEmpty()) {
                        Text(stringResource(R.string.watchers_operations, row.operations.sorted().joinToString(", ")),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (row.packages.isNotEmpty()) {
                        Text(stringResource(R.string.watchers_packages, row.packages.sorted().joinToString(", ")),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (row.operations.isEmpty() && row.packages.isEmpty()) {
                        Text(stringResource(R.string.watchers_scope_unknown), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        if (row.watchingUid == -1) stringResource(R.string.watchers_uid_unrestricted)
                        else stringResource(R.string.watchers_uid_filter, row.watchingUid),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
