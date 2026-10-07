package dev.izumi.appopsnext.presentation.permissions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpNames
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.batch.model.BatchOperationTarget
import dev.izumi.appopsnext.presentation.app_detail.AppOpDisplayCatalog
import dev.izumi.appopsnext.presentation.app_detail.AppOpModeChangeUiState
import dev.izumi.appopsnext.presentation.app_detail.ModeChangeDialog
import dev.izumi.appopsnext.presentation.batch.BatchOperationDialog
import dev.izumi.appopsnext.presentation.batch.BatchOperationUiState
import dev.izumi.appopsnext.presentation.batch.BatchOperationsViewModel
import dev.izumi.appopsnext.presentation.components.AppIcon
import dev.izumi.appopsnext.presentation.components.CompactSearchField
import dev.izumi.appopsnext.presentation.history.HistoryPermissionIconCatalog

@Composable
fun PermissionBrowser(
    apps: List<InstalledApp>,
    appsLoading: Boolean,
    appsFailed: Boolean,
    onRefreshApps: () -> Unit,
    model: PermissionBrowserViewModel = viewModel(),
    batch: BatchOperationsViewModel = viewModel(key = "permission-browser-batch"),
) {
    val state by model.state.collectAsStateWithLifecycle()
    val change by model.change.collectAsStateWithLifecycle()
    val batchState by batch.uiState.collectAsStateWithLifecycle()
    var search by rememberSaveable(state.operation) { mutableStateOf("") }
    var filter by rememberSaveable(state.operation) { mutableStateOf<AppOpMode?>(null) }
    var selecting by rememberSaveable(state.operation) { mutableStateOf(false) }
    var selected by remember(state.operation) { mutableStateOf(emptySet<String>()) }
    var batchMode by rememberSaveable { mutableStateOf(AppOpMode.IGNORE) }
    var info by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val catalog = remember(context) { AppOpDisplayCatalog.build(emptyList(), "", context::getString) }
    val operation = state.operation
    val title = catalog.firstOrNull { it.operationName == operation }?.labelRes?.let { stringResource(it) }
        ?: operation.orEmpty()
    val busy = change is AppOpModeChangeUiState.Applying || batchState is BatchOperationUiState.Running
    val visible = remember(apps, search, filter, state.states) {
        val query = search.trim()
        apps.filter {
            (it.label.contains(query, true) || it.packageName.contains(query, true)) &&
                (filter == null || state.states?.effective(it.uid, it.packageName)?.mode == filter)
        }
    }
    val visibleKeys = visible.map { it.packageName }.toSet()
    LaunchedEffect(visibleKeys) { selected = selected intersect visibleKeys }
    LaunchedEffect(batchState) {
        if (batchState is BatchOperationUiState.Finished) {
            selected = emptySet()
            selecting = false
            model.refresh()
        }
    }
    BackHandler(operation != null) { if (!busy) model.select(null) }

    Column(Modifier.fillMaxSize().imePadding()) {
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (operation != null) {
                IconButton(onClick = { model.select(null) }, enabled = !busy) {
                    Icon(painterResource(R.drawable.ic_ph_arrow_left), stringResource(R.string.permission_back))
                }
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { selecting = !selecting; selected = emptySet() }, enabled = !busy) {
                    Icon(painterResource(if (selecting) R.drawable.ic_ph_x else R.drawable.ic_ph_list_checks),
                        stringResource(if (selecting) R.string.batch_cancel_selection else R.string.batch_action))
                }
            } else Spacer(Modifier.weight(1f))
            IconButton(onClick = { info = true }) {
                Icon(painterResource(R.drawable.ic_ph_info), stringResource(R.string.permission_info_title))
            }
        }
        CompactSearchField(search, { search = it },
            stringResource(if (operation == null) R.string.app_detail_search_label else R.string.app_list_search_label),
            Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        if (operation == null) {
            val permissions = remember(search, context) {
                AppOpDisplayCatalog.build(emptyList(), search, context::getString)
            }
            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(permissions, key = { it.operationName }) { permission ->
                    Surface(onClick = { model.select(permission.operationName) }, shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        ListItem(
                            headlineContent = { Text(permission.labelRes?.let { stringResource(it) } ?: permission.operationName) },
                            supportingContent = { Text(permission.operationName) },
                            leadingContent = { Icon(painterResource(HistoryPermissionIconCatalog.visualFor(permission.operationName).iconRes), null) },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        )
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                ModePicker(filter, { filter = it }, includeAll = true)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = model::refresh, enabled = !busy && !state.loading) {
                    Text(stringResource(R.string.permission_refresh))
                }
            }
            if (selecting) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.batch_selected_count, selected.size), Modifier.weight(1f))
                    TextButton(onClick = {
                        selected = if (selected.containsAll(visibleKeys)) emptySet() else visibleKeys
                    }, enabled = !busy && state.states != null && visibleKeys.isNotEmpty()) {
                        Text(stringResource(if (visibleKeys.isNotEmpty() && selected.containsAll(visibleKeys))
                            R.string.selection_clear_all else R.string.selection_select_all))
                    }
                }
            }
            when {
                state.loading || appsLoading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.failed || appsFailed -> Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.permission_load_failed))
                    TextButton(onClick = { model.refresh(); if (appsFailed) onRefreshApps() }) { Text(stringResource(R.string.action_retry)) }
                }
                else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (visible.isEmpty()) item { Text(stringResource(R.string.app_list_empty_search)) }
                    items(visible, key = { it.packageName }) { app ->
                        val effective = state.states?.effective(app.uid, app.packageName) ?: return@items
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    AppIcon(app.packageName, app.label, size = 36.dp)
                                    Column(Modifier.weight(1f)) {
                                        Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(app.packageName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (selecting) Checkbox(app.packageName in selected, { checked ->
                                        selected = if (checked) selected + app.packageName else selected - app.packageName
                                    }, enabled = !busy)
                                }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(if (effective.scope == AppOpScope.UID) R.string.permission_scope_uid else R.string.permission_scope_package),
                                        Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                                    ModePicker(effective.mode, { it?.let { mode -> model.request(app, mode) } }, enabled = !selecting && !busy)
                                }
                            }
                        }
                    }
                }
            }
            if (selecting) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ModePicker(batchMode, { if (it != null) batchMode = it }, enabled = !busy)
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        batch.requestOperationBatch(title, visible.filter { it.packageName in selected }.map { app ->
                            BatchOperationTarget(app.packageName, app.label, app.uid, AppOpNames.stableName(operation),
                                requireNotNull(state.states).effective(app.uid, app.packageName).scope, batchMode,
                                allowScopeFallback = false)
                        })
                    }, enabled = selected.isNotEmpty() && state.states != null && !appsFailed && !busy) {
                        Text(stringResource(R.string.batch_apply_short))
                    }
                }
            }
        }
    }
    if (info) AlertDialog(onDismissRequest = { info = false }, title = { Text(stringResource(R.string.permission_info_title)) },
        text = { Text(stringResource(R.string.permission_info_body)) }, confirmButton = {
            TextButton(onClick = { info = false }) { Text(stringResource(R.string.permission_close)) }
        })
    ModeChangeDialog(change, model::confirm, model::dismiss, model::dismissDenyNotice, model::dismissFlagNotice, model::requestForeground)
    BatchOperationDialog(batchState, batch::confirm, batch::dismiss)
}

@Composable
private fun ModePicker(mode: AppOpMode?, onMode: (AppOpMode?) -> Unit, includeAll: Boolean = false, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, enabled = enabled) { Text(modeLabel(mode)) }
        DropdownMenu(expanded, { expanded = false }) {
            val modes = if (includeAll) listOf(null) + AppOpMode.entries else AppOpMode.entries
            modes.forEach { entry ->
                DropdownMenuItem(text = { Text(modeLabel(entry)) }, onClick = { expanded = false; onMode(entry) })
            }
        }
    }
}

@Composable
private fun modeLabel(mode: AppOpMode?): String = stringResource(when (mode) {
    null -> R.string.permission_all_modes
    AppOpMode.ALLOW -> R.string.app_op_mode_allow
    AppOpMode.IGNORE -> R.string.app_op_mode_ignore
    AppOpMode.DENY -> R.string.app_op_mode_deny
    AppOpMode.DEFAULT -> R.string.app_op_mode_default
    AppOpMode.FOREGROUND -> R.string.app_op_mode_foreground
})
