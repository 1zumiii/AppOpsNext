package dev.izumi.appopsnext.presentation.permissions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.batch.model.BatchOperationTarget
import dev.izumi.appopsnext.presentation.app_detail.AppOpModeChangeUiState
import dev.izumi.appopsnext.presentation.app_detail.EditableModeMenu
import dev.izumi.appopsnext.presentation.app_detail.modeLabel
import dev.izumi.appopsnext.presentation.app_list.AppListUiState
import dev.izumi.appopsnext.presentation.batch.BatchOperationUiState
import dev.izumi.appopsnext.presentation.batch.BatchSelectionControls
import dev.izumi.appopsnext.presentation.batch.toggleVisibleSelection
import dev.izumi.appopsnext.presentation.components.CompactSearchField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionDetailScreen(
    uiState: PermissionBrowserState,
    appListState: AppListUiState,
    modeChangeState: AppOpModeChangeUiState,
    batchState: BatchOperationUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRefreshApps: () -> Unit,
    onShowInfo: () -> Unit,
    onModeChangeRequested: (InstalledApp, AppOpMode) -> Unit,
    onBatchRequested: (String, List<BatchOperationTarget>) -> Unit,
) {
    val operation = uiState.operation ?: return
    val title = permissionLabel(operation)
    var search by rememberSaveable(operation) { mutableStateOf("") }
    var filter by rememberSaveable(operation) { mutableStateOf<AppOpMode?>(null) }
    var selecting by rememberSaveable(operation) { mutableStateOf(false) }
    var selected by remember(operation) { mutableStateOf(emptySet<String>()) }
    var batchMode by rememberSaveable { mutableStateOf(AppOpMode.IGNORE) }
    val busy = modeChangeState is AppOpModeChangeUiState.Applying ||
        batchState is BatchOperationUiState.Running
    LifecycleResumeEffect(operation) {
        if (!busy && !uiState.loading) onRefresh()
        onPauseOrDispose { }
    }
    val exitSelection = {
        if (!busy) {
            selecting = false
            selected = emptySet()
        }
    }
    BackHandler(enabled = selecting) {
        exitSelection()
    }
    val handleBack = {
        if (selecting) exitSelection() else onBack()
    }
    val canEdit = !busy && !uiState.loading && !uiState.failed &&
        !appListState.isLoading && !appListState.loadFailed && uiState.states != null
    val apps = appListState.allApps
    val visibleApps = remember(apps, search, filter, uiState.states) {
        val query = search.trim()
        apps.filter { app ->
            (app.label.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)) &&
                (filter == null || uiState.states?.effective(app.uid, app.packageName)?.mode == filter)
        }
    }
    val eligibleKeys = apps.map { it.packageName }.toSet()
    val visibleKeys = visibleApps.filter {
        uiState.states?.effective(it.uid, it.packageName)?.mode != null
    }.map { it.packageName }.toSet()
    LaunchedEffect(eligibleKeys, appListState.isLoading) {
        if (!appListState.isLoading) selected = selected intersect eligibleKeys
    }
    LaunchedEffect(batchState) {
        if (batchState is BatchOperationUiState.Finished) {
            selected = emptySet()
            selecting = false
        }
    }
    val targets = uiState.states?.let { states ->
        permissionBatchTargets(
            apps = apps,
            states = states,
            selectedPackages = selected,
            operation = operation,
            requestedMode = batchMode,
        )
    }.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = title) },
                navigationIcon = {
                    IconButton(onClick = handleBack, enabled = !busy) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ph_arrow_left),
                            contentDescription = stringResource(R.string.permission_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            selecting = !selecting
                            selected = emptySet()
                        },
                        enabled = !busy,
                    ) {
                        Icon(
                            painter = painterResource(
                                if (selecting) R.drawable.ic_ph_x else R.drawable.ic_ph_list_checks,
                            ),
                            contentDescription = stringResource(
                                if (selecting) R.string.batch_cancel_selection else R.string.batch_action,
                            ),
                        )
                    }
                    IconButton(onClick = onShowInfo) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ph_info),
                            contentDescription = stringResource(R.string.permission_info_title),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { contentPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding).imePadding()) {
            CompactSearchField(
                value = search,
                onValueChange = { search = it },
                label = stringResource(R.string.app_list_search_label),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            )
            ModeFilters(selectedMode = filter, onModeSelected = { filter = it })
            PullToRefreshBox(
                isRefreshing = uiState.loading || appListState.isLoading,
                onRefresh = {
                    if (!busy) {
                        onRefresh()
                        if (appListState.loadFailed) onRefreshApps()
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when {
                        uiState.failed || appListState.loadFailed -> item {
                            Column(
                                modifier = Modifier.fillParentMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(text = stringResource(R.string.permission_load_failed))
                                TextButton(onClick = {
                                    onRefresh()
                                    if (appListState.loadFailed) onRefreshApps()
                                }) {
                                    Text(text = stringResource(R.string.action_retry))
                                }
                            }
                        }
                        uiState.states == null || appListState.isLoading -> item {
                            Box(
                                modifier = Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        visibleApps.isEmpty() -> item {
                            Text(text = stringResource(R.string.app_list_empty_search))
                        }
                        else -> items(items = visibleApps, key = InstalledApp::packageName) { app ->
                            PermissionAppListItem(
                                app = app,
                                state = uiState.states.effective(app.uid, app.packageName),
                                editEnabled = canEdit,
                                isApplying = (modeChangeState as? AppOpModeChangeUiState.Applying)
                                    ?.request?.packageName == app.packageName,
                                selectedForBatch = if (selecting) app.packageName in selected else null,
                                onSelectionChange = { checked ->
                                    selected = if (checked) selected + app.packageName
                                    else selected - app.packageName
                                },
                                onModeSelected = { mode -> onModeChangeRequested(app, mode) },
                            )
                        }
                    }
                }
            }
            if (selecting) {
                BatchSelectionControls(
                    selectedCount = targets.size,
                    visibleSelectedCount = visibleKeys.count { it in selected },
                    visibleItemCount = visibleKeys.size,
                    onToggleAll = { selected = toggleVisibleSelection(selected, visibleKeys) },
                    enabled = canEdit,
                ) {
                    EditableModeMenu(
                        currentMode = batchMode,
                        enabled = canEdit,
                        onModeSelected = { _, mode -> batchMode = mode },
                        contentDescription = stringResource(
                            R.string.batch_apply_mode_value,
                            modeLabel(batchMode),
                        ),
                    )
                    Button(
                        onClick = { onBatchRequested(title, targets) },
                        enabled = targets.isNotEmpty() && canEdit,
                    ) {
                        Text(text = stringResource(R.string.batch_apply_short))
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeFilters(
    selectedMode: AppOpMode?,
    onModeSelected: (AppOpMode?) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selectedMode == null,
            onClick = { onModeSelected(null) },
            label = { Text(text = stringResource(R.string.permission_all_modes)) },
        )
        AppOpMode.entries.forEach { mode ->
            FilterChip(
                selected = selectedMode == mode,
                onClick = { onModeSelected(mode) },
                label = { Text(text = modeLabel(mode)) },
            )
        }
    }
}
