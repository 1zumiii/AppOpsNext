package dev.izumi.appopsnext.presentation.experimental

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.presentation.components.AppBottomSheet
import dev.izumi.appopsnext.presentation.components.CompactSearchField
import dev.izumi.appopsnext.apps.AppListFilter
import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.monitor.AppOpCodes
import dev.izumi.appopsnext.presentation.app_detail.AppOpDisplayCatalog
import dev.izumi.appopsnext.presentation.components.AppIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorTargetsScreen(
    apps: List<InstalledApp>,
    selectionByPackage: Map<String, Set<String>>,
    onBack: () -> Unit,
    onAppSelected: (InstalledApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    // Watched apps are listed first under their own heading; a count alone does
    // not answer "which ones", which is the question this screen exists for.
    val (watched, others) = remember(apps, query, selectionByPackage) {
        AppListFilter.apply(apps = apps, query = query)
            .partition { selectionByPackage[it.packageName].orEmpty().isNotEmpty() }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.monitor_targets_title),
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
                    text = stringResource(R.string.monitor_targets_hint),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                CompactSearchField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    label = stringResource(R.string.monitor_targets_search),
                )
            }
            if (watched.isNotEmpty()) {
                item {
                    TargetsSectionHeader(
                        text = stringResource(R.string.monitor_targets_selected_header),
                    )
                }
                itemsIndexed(watched, key = { _, app -> app.packageName }) { index, app ->
                    // The row says how many operations are watched rather than
                    // naming them; the list grows past what one row can show and
                    // the names are one tap away.
                    TargetRow(
                        app = app,
                        detail = stringResource(
                            R.string.monitor_selected_count,
                            selectionByPackage[app.packageName].orEmpty().size,
                        ),
                        first = index == 0,
                        last = index == watched.lastIndex,
                        onClick = { onAppSelected(app) },
                    )
                }
            }
            if (others.isNotEmpty()) {
                item {
                    TargetsSectionHeader(
                        text = stringResource(R.string.monitor_targets_other_header),
                    )
                }
                itemsIndexed(others, key = { _, app -> app.packageName }) { index, app ->
                    TargetRow(
                        app = app,
                        detail = app.packageName,
                        first = index == 0,
                        last = index == others.lastIndex,
                        onClick = { onAppSelected(app) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetsSectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun TargetRow(
    app: InstalledApp,
    detail: String,
    first: Boolean,
    last: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(
            topStart = if (first) 18.dp else 0.dp,
            topEnd = if (first) 18.dp else 0.dp,
            bottomStart = if (last) 18.dp else 0.dp,
            bottomEnd = if (last) 18.dp else 0.dp,
        ),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            ListItem(
                modifier = Modifier.clickable(onClick = onClick),
                leadingContent = {
                    AppIcon(packageName = app.packageName, appLabel = app.label)
                },
                headlineContent = {
                    Text(text = app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = {
                    Text(
                        text = detail,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            if (!last) {
                HorizontalDivider(modifier = Modifier.padding(start = 64.dp, end = 16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorOperationsScreen(
    app: InstalledApp,
    selected: Set<String>,
    showAll: Boolean,
    warningSuppressed: Boolean,
    onBack: () -> Unit,
    onSelectionChange: (Set<String>) -> Unit,
    onShowAllChange: (Boolean) -> Unit,
    onSuppressWarning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // The shortlist is a judgement about what usually matters, not a limit of
    // the watch, so everything the operation table knows can be offered instead.
    // An operation that is already picked stays on the list either way, so
    // turning the shortlist back on cannot hide a live selection.
    val options = remember(showAll, selected) {
        val names = if (showAll) {
            AppOpCodes.ALL_NAMES
        } else {
            (AppOpCodes.MONITORED_NAMES + selected).distinct()
        }
        names.map { name ->
            name to (
                AppOpDisplayCatalog.labelResOf(name)
                    ?.let(context::getString)
                    ?: name
                )
        }.sortedBy { it.second }
    }
    var warning by remember { mutableStateOf(false) }
    if (warning) {
        AppBottomSheet(
            onDismissRequest = { warning = false },
            title = { Text(text = stringResource(R.string.monitor_all_ops_warning_title)) },
            text = { Text(text = stringResource(R.string.monitor_all_ops_warning_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        warning = false
                        onShowAllChange(true)
                    },
                ) {
                    Text(text = stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        warning = false
                        onSuppressWarning()
                        onShowAllChange(true)
                    },
                ) {
                    Text(text = stringResource(R.string.action_do_not_ask_again))
                }
            },
        )
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = app.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            Text(
                text = stringResource(R.string.monitor_operations_hint),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn {
                item {
                    ExperimentalListRow(
                        headlineContent = {
                            Text(text = stringResource(R.string.monitor_all_ops_title))
                        },
                        trailingContent = {
                            Switch(
                                checked = showAll,
                                onCheckedChange = { on ->
                                    when {
                                        !on -> onShowAllChange(false)
                                        warningSuppressed -> onShowAllChange(true)
                                        else -> warning = true
                                    }
                                },
                            )
                        },
                    )
                }
                items(options, key = { it.first }) { (name, label) ->
                    val checked = name in selected
                    ExperimentalListRow(
                        modifier = Modifier.clickable {
                            onSelectionChange(
                                if (checked) selected - name else selected + name,
                            )
                        },
                        headlineContent = { Text(text = label) },
                        trailingContent = {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    onSelectionChange(
                                        if (isChecked) selected + name else selected - name,
                                    )
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}
