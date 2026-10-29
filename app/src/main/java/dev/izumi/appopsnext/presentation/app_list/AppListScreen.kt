package dev.izumi.appopsnext.presentation.app_list

import dev.izumi.appopsnext.presentation.components.topFadingEdge
import androidx.activity.compose.BackHandler
import dev.izumi.appopsnext.apps.model.AndroidUser
import dev.izumi.appopsnext.apps.AppSortOrder
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.ui.theme.mainPageHeadingWeight
import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.presentation.batch.TemplatePickerDialog
import dev.izumi.appopsnext.presentation.batch.BatchSelectionControls
import dev.izumi.appopsnext.presentation.batch.toggleVisibleSelection
import dev.izumi.appopsnext.presentation.components.AppIcon
import dev.izumi.appopsnext.presentation.components.AppSortMenu
import dev.izumi.appopsnext.presentation.components.ScrollToTopOnSortChange
import dev.izumi.appopsnext.presentation.components.appSortTimeText
import dev.izumi.appopsnext.presentation.components.CompactSearchField
import dev.izumi.appopsnext.presentation.components.mainPageGroupShape
import dev.izumi.appopsnext.presentation.components.MainPageGroupDivider
import dev.izumi.appopsnext.templates.model.PermissionTemplate
import dev.izumi.appopsnext.presentation.batch.BatchOperationUiState
import androidx.compose.runtime.saveable.rememberSaveable
import dev.izumi.appopsnext.presentation.batch.StringSelectionSaver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    uiState: AppListUiState,
    batchState: BatchOperationUiState,
    onSearchQueryChange: (String) -> Unit,
    onSortOrderChange: (AppSortOrder) -> Unit,
    onRefresh: () -> Unit,
    onUserSelected: (Int?) -> Unit,
    onAppSelected: (InstalledApp) -> Unit,
    templates: List<PermissionTemplate>,
    onTemplateApplyRequested:
        (PermissionTemplate, List<InstalledApp>) -> Unit,
    permissionTab: Boolean,
    onPermissionTabChange: (Boolean) -> Unit,
    onPermissionInfo: () -> Unit,
    permissionContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
) {
    var batchSelectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedPackages by rememberSaveable(stateSaver = StringSelectionSaver) {
        mutableStateOf(emptySet<String>())
    }
    var showTemplatePicker by rememberSaveable { mutableStateOf(false) }
    var awaitingBatchResult by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(batchState) {
        if (awaitingBatchResult && batchState is BatchOperationUiState.Finished) {
            batchSelectionMode = false
            selectedPackages = emptySet()
            awaitingBatchResult = false
        } else if (batchState is BatchOperationUiState.Idle) {
            awaitingBatchResult = false
        }
    }
    BackHandler(enabled = batchSelectionMode && !permissionTab && !showTemplatePicker) {
        batchSelectionMode = false
        selectedPackages = emptySet()
    }
    val visiblePackages = uiState.visibleApps.map { it.packageName }.toSet()
    val eligiblePackages = uiState.allApps.map { it.packageName }.toSet()
    LaunchedEffect(eligiblePackages, uiState.isLoading) {
        if (!uiState.isLoading) selectedPackages = selectedPackages intersect eligiblePackages
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    // Which space is listed reads as part of the title, apart from
                    // the tabs, which choose how it is browsed.
                    Column {
                        Text(
                            text = stringResource(R.string.app_list_title),
                            fontWeight = mainPageHeadingWeight(),
                        )
                        if (uiState.users.size > 1) {
                            UserPicker(
                                users = uiState.users,
                                selectedUserId = uiState.selectedUserId ?: uiState.currentUserId,
                                onUserSelected = { id ->
                                    batchSelectionMode = false
                                    selectedPackages = emptySet()
                                    onUserSelected(id.takeIf { it != uiState.currentUserId })
                                },
                            )
                        }
                    }
                },
                actions = {
                    if (!permissionTab) {
                        AppSortMenu(selected = uiState.sortOrder, onSelected = onSortOrderChange)
                    }
                    if (!permissionTab) {
                        IconButton(
                            onClick = {
                                batchSelectionMode = !batchSelectionMode
                                selectedPackages = emptySet()
                            },
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (batchSelectionMode) R.drawable.ic_ph_x
                                    else R.drawable.ic_ph_list_checks,
                                ),
                                contentDescription = stringResource(
                                    if (batchSelectionMode) R.string.batch_cancel_selection
                                    else R.string.batch_action,
                                ),
                            )
                        }
                    } else {
                        IconButton(onClick = onPermissionInfo) {
                            Icon(
                                painter = painterResource(R.drawable.ic_ph_info),
                                contentDescription = stringResource(R.string.permission_info_title),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = bottomBar,
    ) { contentPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            TabRow(
                selectedTabIndex = if (permissionTab) 1 else 0,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                Tab(
                    selected = !permissionTab,
                    onClick = { onPermissionTabChange(false) },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = { Text(stringResource(R.string.browse_by_app), style = MaterialTheme.typography.labelLarge) },
                )
                Tab(
                    selected = permissionTab,
                    onClick = { onPermissionTabChange(true) },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = { Text(stringResource(R.string.browse_by_permission), style = MaterialTheme.typography.labelLarge) },
                )
            }
            if (permissionTab) {
                permissionContent()
            } else {
                when {
                    uiState.isLoading && uiState.visibleApps.isEmpty() -> LoadingContent(
                        modifier = Modifier.fillMaxSize(),
                    )
                    uiState.loadFailed && uiState.visibleApps.isEmpty() -> LoadFailureContent(
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> AppListContent(
                        uiState = uiState,
                        onRefresh = onRefresh,
                        onSearchQueryChange = onSearchQueryChange,
                        onAppSelected = onAppSelected,
                        batchSelectionMode = batchSelectionMode,
                        selectedPackages = selectedPackages,
                        onBatchSelectionChange = { packageName, selected ->
                            selectedPackages = if (selected) selectedPackages + packageName
                            else selectedPackages - packageName
                        },
                        onApplyTemplate = { showTemplatePicker = true },
                        onToggleAll = {
                            selectedPackages = toggleVisibleSelection(selectedPackages, visiblePackages)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    if (showTemplatePicker) {
        TemplatePickerDialog(
            templates = templates,
            onSelect = { template ->
                showTemplatePicker = false
                awaitingBatchResult = true
                onTemplateApplyRequested(
                    template,
                    uiState.allApps.filter {
                        it.packageName in selectedPackages
                    },
                )
            },
            onDismiss = { showTemplatePicker = false },
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AppListContent(
    uiState: AppListUiState,
    onRefresh: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onAppSelected: (InstalledApp) -> Unit,
    batchSelectionMode: Boolean,
    selectedPackages: Set<String>,
    onBatchSelectionChange: (String, Boolean) -> Unit,
    onApplyTemplate: () -> Unit,
    onToggleAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    ScrollToTopOnSortChange(uiState.sortOrder, listState)
    Column(
        modifier = modifier,
    ) {
        CompactSearchField(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChange,
            label = stringResource(R.string.app_list_search_label),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 12.dp),
        )
        if (uiState.searchQuery.isNotBlank() || uiState.visibleApps.size != uiState.totalAppCount) {
            Text(
                text = stringResource(
                    R.string.app_list_count,
                    uiState.visibleApps.size,
                    uiState.totalAppCount,
                ),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = { if (!uiState.isLoading) onRefresh() },
            modifier = Modifier.weight(1f),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().topFadingEdge(listState),
                state = listState,
                contentPadding = PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 8.dp,
                    bottom = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (uiState.visibleApps.isEmpty()) {
                    item {
                        EmptySearchContent(modifier = Modifier.fillParentMaxSize())
                    }
                }
                items(
                    items = uiState.visibleApps,
                    key = InstalledApp::packageName,
                ) { app ->
                    InstalledAppListItem(
                        app = app,
                        sortOrder = uiState.sortOrder,
                        selectedForBatch = if (batchSelectionMode) {
                            app.packageName in selectedPackages
                        } else {
                            null
                        },
                        onClick = {
                            if (batchSelectionMode) {
                                onBatchSelectionChange(
                                    app.packageName,
                                    app.packageName !in selectedPackages,
                                )
                            } else {
                                onAppSelected(app)
                            }
                        },
                        onBatchSelectionChange = { selected ->
                            onBatchSelectionChange(
                                app.packageName,
                                selected,
                            )
                        },
                    )
                }
            }
        }
        if (batchSelectionMode) {
            BatchSelectionControls(
                selectedCount = selectedPackages.size,
                visibleSelectedCount = uiState.visibleApps.count {
                    it.packageName in selectedPackages
                },
                visibleItemCount = uiState.visibleApps.size,
                onToggleAll = onToggleAll,
            ) {
                FilledTonalButton(
                    onClick = onApplyTemplate,
                    enabled = selectedPackages.isNotEmpty(),
                ) {
                    Text(text = stringResource(R.string.batch_apply_template))
                }
            }
        }
    }
}

@Composable
private fun InstalledAppListItem(
    app: InstalledApp,
    sortOrder: AppSortOrder,
    selectedForBatch: Boolean?,
    onClick: () -> Unit,
    onBatchSelectionChange: (Boolean) -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(
                    packageName = app.packageName,
                    appLabel = app.label,
                    size = 40.dp,
                    userId = app.userId,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = app.packageName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    appSortTimeText(app, sortOrder)?.let { time ->
                        Text(
                            text = time,
                            maxLines = 1,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                selectedForBatch?.let { selected ->
                    Checkbox(
                        checked = selected,
                        onCheckedChange = onBatchSelectionChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun UserPicker(
    users: List<AndroidUser>,
    selectedUserId: Int,
    onUserSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedUser = users.firstOrNull { it.id == selectedUserId }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_ph_user),
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp).size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = selectedUser?.let { userDisplayName(it) }
                    ?: stringResource(R.string.user_unnamed, selectedUserId),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                painter = painterResource(R.drawable.ic_ph_caret_right),
                contentDescription = stringResource(R.string.user_select_space),
                modifier = Modifier.padding(start = 4.dp).size(14.dp).rotate(90f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            users.forEach { user ->
                DropdownMenuItem(
                    onClick = { expanded = false; onUserSelected(user.id) },
                    text = {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                text = userDisplayName(user),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = stringResource(R.string.user_id, user.id),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    // On the trailing side, like the sort menu, so every name starts at one edge.
                    trailingIcon = if (user.id == selectedUserId) {
                        {
                            Icon(
                                painter = painterResource(R.drawable.ic_ph_check_circle),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/**
 * The system names some profiles in English whatever the language, so the
 * names it is known to use are shown in the app's language instead.
 */
@Composable
private fun userDisplayName(user: AndroidUser): String = when (user.name.trim()) {
    "" -> stringResource(R.string.user_unnamed, user.id)
    "Private space" -> stringResource(R.string.user_private_space)
    "Work profile" -> stringResource(R.string.user_work_profile)
    else -> user.name
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun LoadFailureContent(
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.app_list_load_failed),
            style = MaterialTheme.typography.titleMedium,
        )
        FilledTonalButton(
            onClick = onRefresh,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(text = stringResource(R.string.action_retry))
        }
    }
}

@Composable
private fun EmptySearchContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.app_list_empty_search),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
