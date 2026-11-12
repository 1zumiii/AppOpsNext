package dev.izumi.appopsnext.presentation.experimental

import dev.izumi.appopsnext.presentation.components.EmptyStateIcons
import dev.izumi.appopsnext.presentation.components.EmptyStateContent
import dev.izumi.appopsnext.presentation.components.topFadingEdge
import dev.izumi.appopsnext.apps.AppSortOrder
import dev.izumi.appopsnext.presentation.components.AppSortMenu
import dev.izumi.appopsnext.presentation.components.ScrollToTopOnSortChange
import dev.izumi.appopsnext.presentation.components.appSortTimeText
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import dev.izumi.appopsnext.presentation.components.CompactSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import dev.izumi.appopsnext.presentation.batch.BatchSelectionControls
import dev.izumi.appopsnext.presentation.batch.StringSelectionSaver
import dev.izumi.appopsnext.presentation.batch.toggleVisibleSelection
import dev.izumi.appopsnext.presentation.batch.selectedFirstOrdering
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import dev.izumi.appopsnext.AppOpsNextApplication
import dev.izumi.appopsnext.appops.model.MiuiOps
import dev.izumi.appopsnext.monitor.AppOpCodes
import dev.izumi.appopsnext.monitor.MonitorOpCodes
import dev.izumi.appopsnext.monitor.VendorMonitorOps
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.izumi.appopsnext.presentation.app_detail.AppOpDisplayCatalog
import dev.izumi.appopsnext.presentation.components.AppIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorTargetsScreen(
    apps: List<InstalledApp>,
    selectionByPackage: Map<String, Set<String>>,
    sortOrder: AppSortOrder,
    onSortOrderChange: (AppSortOrder) -> Unit,
    onBack: () -> Unit,
    onAppSelected: (InstalledApp) -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.monitor_targets_title),
    hint: String = stringResource(R.string.monitor_targets_hint),
    /**
     * Non-null turns the list into a multiple choice, as applying a monitor
     * template needs: a tap ticks the app instead of opening it, and the bar at
     * the bottom holds [pickActions].
     */
    picked: Set<String>? = null,
    onPickedChange: (Set<String>) -> Unit = {},
    pickActions: @Composable RowScope.() -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    // Watched apps are listed first under their own heading; a count alone does
    // not answer "which ones", which is the question this screen exists for.
    val (watched, others) = remember(apps, query, selectionByPackage) {
        AppListFilter.apply(apps = apps, query = query)
            .partition { selectionByPackage[it.packageName].orEmpty().isNotEmpty() }
    }
    val pick: (InstalledApp) -> Unit = { app ->
        if (picked == null) {
            onAppSelected(app)
        } else {
            onPickedChange(
                if (app.packageName in picked) picked - app.packageName else picked + app.packageName,
            )
        }
    }
    val listState = rememberLazyListState()
    ScrollToTopOnSortChange(sortOrder, listState)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
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
                actions = {
                    AppSortMenu(selected = sortOrder, onSelected = onSortOrderChange)
                },
            )
        },
        bottomBar = {
            if (picked != null) {
                val visible = (watched + others).map(InstalledApp::packageName).toSet()
                BatchSelectionControls(
                    selectedCount = picked.size,
                    visibleSelectedCount = visible.count(picked::contains),
                    visibleItemCount = visible.size,
                    onToggleAll = { onPickedChange(toggleVisibleSelection(picked, visible)) },
                    modifier = Modifier.navigationBarsPadding(),
                    actions = pickActions,
                )
            }
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .topFadingEdge(listState),
            state = listState,
        ) {
            item {
                Text(
                    text = hint,
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
            if (watched.isEmpty() && others.isEmpty()) {
                item {
                    val searching = query.isNotBlank()
                    EmptyStateContent(
                        icon = if (searching) EmptyStateIcons.NO_MATCHES else R.drawable.ic_ph_squares_four,
                        title = stringResource(
                            if (searching) R.string.app_list_empty_search else R.string.app_list_empty,
                        ),
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .padding(vertical = 48.dp),
                    )
                }
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
                        time = appSortTimeText(app, sortOrder),
                        first = index == 0,
                        last = index == watched.lastIndex,
                        checked = picked?.let { app.packageName in it },
                        onClick = { pick(app) },
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
                        time = appSortTimeText(app, sortOrder),
                        first = index == 0,
                        last = index == others.lastIndex,
                        checked = picked?.let { app.packageName in it },
                        onClick = { pick(app) },
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
    time: String?,
    first: Boolean,
    last: Boolean,
    /** Null when the row opens the app rather than ticking it. */
    checked: Boolean?,
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
                    Column {
                        Text(
                            text = detail,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        time?.let { Text(text = it, maxLines = 1) }
                    }
                },
                trailingContent = checked?.let {
                    { Checkbox(checked = it, onCheckedChange = { onClick() }) }
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
    /** What the operations are picked for: an app, or a monitor template. */
    title: String,
    /** Keeps this screen's own state apart from another subject's. */
    stateKey: String,
    hint: String,
    selected: Set<String>,
    showAll: Boolean,
    warningSuppressed: Boolean,
    onBack: () -> Unit,
    onSelectionChange: (Set<String>) -> Unit,
    onShowAllChange: (Boolean) -> Unit,
    onSuppressWarning: () -> Unit,
    modifier: Modifier = Modifier,
    /** Packages to read first when finding out whether the device has MIUI private operations. */
    probePackages: List<String> = emptyList(),
) {
    val context = LocalContext.current
    // MIUI private operations join the full list only once the device has
    // shown that it has them, so every other device sees the list unchanged.
    val vendorCodes by VendorMonitorOps.codes.collectAsStateWithLifecycle()
    LaunchedEffect(showAll) {
        if (showAll) (context.applicationContext as AppOpsNextApplication).probeVendorOps(probePackages)
    }
    val vendorNames = remember(showAll, vendorCodes) {
        if (showAll) MonitorOpCodes.vendorNames(vendorCodes) else emptyList()
    }
    var query by rememberSaveable(stateKey) { mutableStateOf("") }
    val initialSelection by rememberSaveable(stateKey, stateSaver = StringSelectionSaver) {
        mutableStateOf(selected.toSet())
    }
    // The shortlist is a judgement about what usually matters, not a limit of
    // the watch, so everything the operation table knows can be offered instead.
    // An operation that is already picked stays on the list either way, so
    // turning the shortlist back on cannot hide a live selection.
    val options = remember(showAll, selected, initialSelection, context, vendorNames) {
        val names = if (showAll) {
            (AppOpCodes.ALL_NAMES + vendorNames + initialSelection + selected).distinct()
        } else {
            (AppOpCodes.MONITORED_NAMES + initialSelection + selected).distinct()
        }
        names.map { name ->
            name to AppOpDisplayCatalog.displayLabel(name, context.resources)
        }.sortedBy { it.second }
    }
    val orderedOptions = remember(options, initialSelection) {
        val selectedInNameOrder = options.map { it.first }.filter { it in initialSelection }.toSet()
        selectedFirstOrdering(options, selectedInNameOrder) { it.first }
    }
    val visibleOptions = remember(orderedOptions, query, showAll) {
        if (!showAll || query.isBlank()) orderedOptions else orderedOptions.filter { (name, label) ->
            name.contains(query, ignoreCase = true) || label.contains(query, ignoreCase = true)
        }
    }
    var warning by remember { mutableStateOf(false) }
    val requestShowAll: (Boolean) -> Unit = { on ->
        when {
            !on -> {
                query = ""
                onShowAllChange(false)
            }
            warningSuppressed -> onShowAllChange(true)
            else -> warning = true
        }
    }
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
                        text = title,
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                Text(
                    text = hint,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    ListItem(
                        modifier = Modifier.clickable { requestShowAll(!showAll) },
                        headlineContent = { Text(stringResource(R.string.monitor_all_ops_title)) },
                        supportingContent = { Text(stringResource(R.string.monitor_all_ops_summary)) },
                        trailingContent = {
                            CompactSwitch(checked = showAll, onCheckedChange = requestShowAll)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            if (vendorNames.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.monitor_vendor_ops_hint),
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.monitor_operations_section),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.batch_selected_count, selected.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (showAll) {
                item {
                    CompactSearchField(
                        value = query,
                        onValueChange = { query = it },
                        label = stringResource(R.string.monitor_operations_search),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            if (visibleOptions.isEmpty()) {
                item {
                    EmptyStateContent(
                        icon = EmptyStateIcons.NO_MATCHES,
                        title = stringResource(R.string.monitor_operations_empty),
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .padding(vertical = 48.dp),
                    )
                }
            }
            itemsIndexed(visibleOptions, key = { _, option -> option.first }) { index, (name, label) ->
                val checked = name in selected
                Column {
                    ListItem(
                        modifier = Modifier.clickable {
                            onSelectionChange(if (checked) selected - name else selected + name)
                        },
                        headlineContent = {
                            Text(text = label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        },
                        // Said on the row itself, so the warning needs no dialog.
                        supportingContent = MiuiOps.codeOf(name)?.let { code ->
                            { Text(text = stringResource(R.string.monitor_vendor_op_item, code)) }
                        },
                        trailingContent = {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    onSelectionChange(if (isChecked) selected + name else selected - name)
                                },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    if (index < visibleOptions.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
                    }
                }
            }
        }
    }
}
