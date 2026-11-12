package dev.izumi.appopsnext.presentation.app_detail

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.AppOpsNextApplication
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.appops.changes.AppOpChangeRecords
import dev.izumi.appopsnext.appops.model.AppOpEntry
import dev.izumi.appopsnext.appops.model.AppOpNames
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import dev.izumi.appopsnext.presentation.batch.BatchSelectionControls
import dev.izumi.appopsnext.presentation.batch.BatchOperationUiState
import androidx.compose.runtime.saveable.rememberSaveable
import dev.izumi.appopsnext.presentation.batch.StringSelectionSaver
import dev.izumi.appopsnext.presentation.batch.toggleVisibleSelection
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpsReadFailureReason
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.presentation.batch.PermissionBatchSelection
import dev.izumi.appopsnext.presentation.batch.TemplatePickerDialog
import dev.izumi.appopsnext.presentation.components.AppIcon
import dev.izumi.appopsnext.presentation.components.CompactSearchField
import dev.izumi.appopsnext.presentation.components.EmptyStateContent
import dev.izumi.appopsnext.presentation.components.EmptyStateIcons
import dev.izumi.appopsnext.ui.theme.mainPageHeadingWeight
import dev.izumi.appopsnext.templates.model.PermissionTemplate
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    uiState: AppDetailUiState,
    batchState: BatchOperationUiState,
    modeChangeState: AppOpModeChangeUiState,
    searchQuery: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onModeChangeRequested: (
        String,
        AppOpScope,
        AppOpMode,
        AppOpMode,
    ) -> Unit,
    onModeChangeConfirmed: (guard: Boolean) -> Unit,
    onModeChangeDismissed: () -> Unit,
    onDenyFallbackNoticeDismissed: (Boolean) -> Unit,
    onPermissionFlagNoticeDismissed: (Boolean) -> Unit,
    onForegroundAlternativeRequested: () -> Unit,
    templates: List<PermissionTemplate>,
    onTemplateApplyRequested:
        (PermissionTemplate, InstalledApp) -> Unit,
    onPermissionBatchRequested: (
        InstalledApp,
        List<PermissionBatchSelection>,
        AppOpMode,
    ) -> Unit,
    onOpenConnectionSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = uiState.appOrNull()
    var batchSelectionMode by rememberSaveable(app?.packageName) {
        mutableStateOf(false)
    }
    var selectedBatchKeys by rememberSaveable(app?.packageName, stateSaver = StringSelectionSaver) {
        mutableStateOf(emptySet<String>())
    }
    var selectedBatchMode by rememberSaveable(app?.packageName) {
        mutableStateOf(AppOpMode.IGNORE)
    }
    var showTemplatePicker by rememberSaveable(app?.packageName) {
        mutableStateOf(false)
    }
    var awaitingBatchResult by rememberSaveable(app?.packageName) { mutableStateOf(false) }
    LaunchedEffect(batchState) {
        if (awaitingBatchResult && batchState is BatchOperationUiState.Finished) {
            batchSelectionMode = false
            selectedBatchKeys = emptySet()
            awaitingBatchResult = false
        } else if (batchState is BatchOperationUiState.Idle) {
            awaitingBatchResult = false
        }
    }
    val exitBatchSelection = {
        batchSelectionMode = false
        selectedBatchKeys = emptySet()
    }
    BackHandler(enabled = batchSelectionMode && !showTemplatePicker) {
        exitBatchSelection()
    }
    val handleBack = {
        if (batchSelectionMode) exitBatchSelection() else onBack()
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = app?.label
                            ?: stringResource(R.string.app_detail_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = mainPageHeadingWeight(),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ph_arrow_left),
                            contentDescription = stringResource(
                                R.string.action_back,
                            ),
                        )
                    }
                },
                actions = {
                    if (uiState is AppDetailUiState.Ready && app != null) {
                        IconButton(onClick = { showTemplatePicker = true }) {
                            Icon(
                                painter = painterResource(
                                    R.drawable.ic_ph_file_text,
                                ),
                                contentDescription = stringResource(
                                    R.string.batch_apply_template,
                                ),
                            )
                        }
                        IconButton(
                            onClick = {
                                batchSelectionMode = !batchSelectionMode
                                selectedBatchKeys = emptySet()
                            },
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (batchSelectionMode) {
                                        R.drawable.ic_ph_x
                                    } else {
                                        R.drawable.ic_ph_list_checks
                                    },
                                ),
                                contentDescription = stringResource(
                                    if (batchSelectionMode) {
                                        R.string.batch_cancel_selection
                                    } else {
                                        R.string.batch_action
                                    },
                                ),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { contentPadding ->
        when (uiState) {
            AppDetailUiState.Idle -> LoadingContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )

            // The top bar already names the app, so the page is left to the wait.
            is AppDetailUiState.WaitingForBackend -> EmptyStateContent(
                icon = R.drawable.ic_ph_plugs,
                title = stringResource(R.string.app_detail_waiting_backend_title),
                detail = stringResource(R.string.app_detail_waiting_backend),
                actionLabel = stringResource(R.string.app_detail_open_connection),
                onAction = onOpenConnectionSettings,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )

            is AppDetailUiState.Loading -> DetailLoadingContent(
                app = uiState.app,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )

            is AppDetailUiState.Ready -> ReadyContent(
                state = uiState,
                modeChangeState = modeChangeState,
                searchQuery = searchQuery,
                batchSelectionMode = batchSelectionMode,
                selectedBatchKeys = selectedBatchKeys,
                selectedBatchMode = selectedBatchMode,
                onSearchQueryChange = onSearchQueryChange,
                onBatchSelectionChange = { key, selected ->
                    selectedBatchKeys = if (selected) {
                        selectedBatchKeys + key
                    } else {
                        selectedBatchKeys - key
                    }
                },
                onBatchModeChange = { selectedBatchMode = it },
                onApplyPermissionBatch = { selections ->
                    awaitingBatchResult = true
                    onPermissionBatchRequested(
                        uiState.app,
                        selections,
                        selectedBatchMode,
                    )
                },
                onModeChangeRequested = onModeChangeRequested,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )

            is AppDetailUiState.Failure -> FailureContent(
                reason = uiState.reason,
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )
        }
    }

    ModeChangeDialog(
        state = modeChangeState,
        onConfirm = onModeChangeConfirmed,
        onDismiss = onModeChangeDismissed,
        onDenyFallbackNoticeDismiss =
            onDenyFallbackNoticeDismissed,
        onPermissionFlagNoticeDismiss =
            onPermissionFlagNoticeDismissed,
        onForegroundAlternativeRequested =
            onForegroundAlternativeRequested,
    )

    if (showTemplatePicker && app != null) {
        TemplatePickerDialog(
            templates = templates,
            onSelect = { template ->
                showTemplatePicker = false
                onTemplateApplyRequested(template, app)
            },
            onDismiss = { showTemplatePicker = false },
        )
    }
}

@Composable
private fun ReadyContent(
    state: AppDetailUiState.Ready,
    modeChangeState: AppOpModeChangeUiState,
    searchQuery: String,
    batchSelectionMode: Boolean,
    selectedBatchKeys: Set<String>,
    selectedBatchMode: AppOpMode,
    onSearchQueryChange: (String) -> Unit,
    onBatchSelectionChange: (String, Boolean) -> Unit,
    onBatchModeChange: (AppOpMode) -> Unit,
    onApplyPermissionBatch: (List<PermissionBatchSelection>) -> Unit,
    onModeChangeRequested: (
        String,
        AppOpScope,
        AppOpMode,
        AppOpMode,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    val applyingRequest =
        (modeChangeState as? AppOpModeChangeUiState.Applying)?.request
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val alternateLocale = remember(configuration) {
        if (configuration.locales[0]?.language == Locale.ENGLISH.language) {
            Locale.SIMPLIFIED_CHINESE
        } else {
            Locale.ENGLISH
        }
    }
    val alternateContext = remember(context, configuration, alternateLocale) {
        context.createConfigurationContext(
            Configuration(configuration).apply {
                setLocale(alternateLocale)
            },
        )
    }
    val entries = state.snapshot.entries
    val application = context.applicationContext as AppOpsNextApplication
    val changeLog = application.appOpChangeLog
    val changes by changeLog.records.collectAsStateWithLifecycle(emptyList())
    val changeScope = rememberCoroutineScope()
    val appUserId = state.app.userId ?: application.userAppsRepository.currentUserId
    val packageChanges = remember(changes, state.app, appUserId) {
        AppOpChangeRecords.forPackage(
            changes, appUserId, state.app.packageName, state.app.firstInstallTimeMillis,
        )
    }
    // The modes on screen trail a write of this app for a moment, so a setting
    // that looks changed elsewhere here is not flagged from here. The monitor
    // reads it again between writes and flags it in the log if it still is.
    val seenChangedElsewhere = remember(packageChanges, entries) {
        val shown = entries.mapTo(HashSet()) { AppOpNames.stableName(it.name) }
        packageChanges.filter {
            !it.changedElsewhere && it.stableOperationName in shown &&
                entries.modeAt(it.stableOperationName, it.scope) != it.recordedMode
        }
    }
    LaunchedEffect(seenChangedElsewhere) {
        if (seenChangedElsewhere.isNotEmpty()) application.appOpChangeDriftMonitor.refresh()
    }
    val allDisplayItems = remember(entries, context, alternateContext) {
        AppOpDisplayCatalog.build(
            entries = entries,
            query = "",
            labelResolver = context::getString,
            alternateLabelResolver = alternateContext::getString,
        )
    }
    val displayItems = remember(entries, searchQuery, context, alternateContext, allDisplayItems) {
        if (searchQuery.isBlank()) allDisplayItems else AppOpDisplayCatalog.build(
            entries = entries,
            query = searchQuery,
            labelResolver = context::getString,
            alternateLabelResolver = alternateContext::getString,
        )
    }
    val selectedItems = remember(allDisplayItems, selectedBatchKeys) {
        allDisplayItems.filter { it.batchSelectionKey() in selectedBatchKeys }
    }
    val visibleKeys = displayItems.map { it.batchSelectionKey() }.toSet()
    LaunchedEffect(allDisplayItems, selectedBatchKeys) {
        val eligibleKeys = allDisplayItems.map { it.batchSelectionKey() }.toSet()
        (selectedBatchKeys - eligibleKeys).forEach { onBatchSelectionChange(it, false) }
    }
    val totalOperationCount = allDisplayItems.size
    Column(modifier = modifier.imePadding()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = DetailContentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppSummaryCard(
                    app = state.app,
                    operationCount = totalOperationCount,
                )
            }
            item {
                CompactSearchField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    label = stringResource(R.string.app_detail_search_label),
                )
            }
            if (searchQuery.isNotBlank() || displayItems.size != totalOperationCount) {
                item {
                    Text(
                        text = stringResource(
                            R.string.app_detail_filtered_count,
                            displayItems.size,
                            totalOperationCount,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (displayItems.isEmpty()) {
                item {
                    val noRecords = state.snapshot.entries.isEmpty()
                    EmptyStateContent(
                        icon = if (noRecords) R.drawable.ic_ph_shield else EmptyStateIcons.NO_MATCHES,
                        title = stringResource(
                            if (noRecords) {
                                R.string.app_detail_no_operations
                            } else {
                                R.string.app_detail_no_matching_operations
                            },
                        ),
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .padding(vertical = 48.dp),
                    )
                }
            } else {
                itemsIndexed(
                    items = displayItems,
                    key = { index, item ->
                        "$index:${item.scope}:${item.operationName}"
                    },
                ) { _, item ->
                    AppOpListItem(
                        item = item,
                        isApplying =
                            applyingRequest?.matches(item) == true,
                        editEnabled =
                            applyingRequest == null && !batchSelectionMode,
                        selectedForBatch = if (batchSelectionMode) {
                            item.batchSelectionKey() in selectedBatchKeys
                        } else {
                            null
                        },
                        onBatchSelectionChange = { selected ->
                            onBatchSelectionChange(
                                item.batchSelectionKey(),
                                selected,
                            )
                        },
                        onModeSelected = { originalMode, requestedMode ->
                            onModeChangeRequested(
                                item.operationName,
                                item.scope,
                                originalMode,
                                requestedMode,
                            )
                        },
                        changeStatus = AppOpChangeRecords.status(
                            packageRecords = packageChanges,
                            operationName = item.operationName,
                        ) { scope -> entries.modeAt(item.operationName, scope) },
                        onRestoreChange = { status ->
                            status.currentMode?.let { current ->
                                onModeChangeRequested(
                                    item.operationName,
                                    status.record.scope,
                                    current,
                                    status.record.recordedMode,
                                )
                            }
                        },
                        onKeepCurrent = {
                            changeScope.launch {
                                changeLog.forget(appUserId, state.app.packageName, item.operationName)
                            }
                        },
                    )
                }
            }
        }
        if (batchSelectionMode) {
            BatchPermissionControls(
                selectedCount = selectedItems.size,
                visibleSelectedCount = visibleKeys.count { it in selectedBatchKeys },
                visibleItemCount = visibleKeys.size,
                onToggleAll = {
                    val updated = toggleVisibleSelection(selectedBatchKeys, visibleKeys)
                    visibleKeys.forEach { onBatchSelectionChange(it, it in updated) }
                },
                selectedMode = selectedBatchMode,
                onModeChange = onBatchModeChange,
                onApply = {
                    onApplyPermissionBatch(
                        selectedItems.map {
                            PermissionBatchSelection(
                                operationName = it.operationName,
                                scope = it.scope,
                            )
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun BatchPermissionControls(
    selectedCount: Int,
    visibleSelectedCount: Int,
    visibleItemCount: Int,
    onToggleAll: () -> Unit,
    selectedMode: AppOpMode,
    onModeChange: (AppOpMode) -> Unit,
    onApply: () -> Unit,
) {
    BatchSelectionControls(
        selectedCount = selectedCount,
        visibleSelectedCount = visibleSelectedCount,
        visibleItemCount = visibleItemCount,
        onToggleAll = onToggleAll,
    ) {
        EditableModeMenu(
            currentMode = selectedMode,
            enabled = true,
            onModeSelected = { _, mode -> onModeChange(mode) },
            contentDescription = stringResource(
                R.string.batch_apply_mode_value,
                modeLabel(selectedMode),
            ),
        )
        Button(onClick = onApply, enabled = selectedCount > 0) {
            Text(text = stringResource(R.string.batch_apply_short))
        }
    }
}

/** The mode stored at [scope], [AppOpMode.DEFAULT] where none is, null where it has no known name. */
private fun List<AppOpEntry>.modeAt(operationName: String, scope: AppOpScope): AppOpMode? {
    val shellName = AppOpNames.shellName(operationName)
    val entry = firstOrNull { it.scope == scope && AppOpNames.shellName(it.name) == shellName }
        ?: return AppOpMode.DEFAULT
    return AppOpMode.fromShellValue(entry.mode)
}

private fun AppOpDisplayItem.batchSelectionKey(): String =
    "${scope.name}:$operationName"

@Composable
private fun AppSummaryCard(
    app: InstalledApp,
    operationCount: Int?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(
                packageName = app.packageName,
                appLabel = app.label,
                size = 44.dp,
                userId = app.userId,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        if (app.isSystemApp) {
                            R.string.app_type_system
                        } else {
                            R.string.app_type_user
                        },
                        app.uid,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = operationCount?.let {
                        stringResource(R.string.app_detail_operation_count, it)
                    } ?: stringResource(R.string.app_detail_loading_operations),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun DetailLoadingContent(
    app: InstalledApp,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = DetailContentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            AppSummaryCard(
                app = app,
                operationCount = null,
            )
        }
        item {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        item {
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            )
        }
        items(SKELETON_ROW_COUNT) {
            AppOpSkeletonItem()
        }
    }
}

@Composable
private fun AppOpSkeletonItem() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                SkeletonBlock(
                    modifier = Modifier
                        .width(92.dp)
                        .height(12.dp),
                )
                SkeletonBlock(
                    modifier = Modifier
                        .width(168.dp)
                        .height(20.dp),
                )
                SkeletonBlock(
                    modifier = Modifier
                        .width(132.dp)
                        .height(14.dp),
                )
            }
            SkeletonBlock(
                modifier = Modifier
                    .width(88.dp)
                    .height(40.dp),
            )
        }
    }
}

@Composable
private fun SkeletonBlock(modifier: Modifier) {
    Box(
        modifier = modifier.background(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = SKELETON_ALPHA,
            ),
            shape = RoundedCornerShape(6.dp),
        ),
    )
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
private fun FailureContent(
    reason: AppOpsReadFailureReason,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyStateContent(
        icon = EmptyStateIcons.LOAD_FAILED,
        title = stringResource(R.string.app_detail_load_failed_title),
        detail = stringResource(
            when (reason) {
                AppOpsReadFailureReason.BACKEND_UNAVAILABLE ->
                    R.string.status_appops_backend_unavailable_detail

                AppOpsReadFailureReason.COMMAND_FAILED ->
                    R.string.status_appops_command_failed_detail

                AppOpsReadFailureReason.COMMAND_TIMED_OUT ->
                    R.string.status_appops_command_timed_out_detail
            },
        ),
        actionLabel = stringResource(R.string.action_retry),
        onAction = onRefresh,
        modifier = modifier,
    )
}

private fun AppDetailUiState.appOrNull(): InstalledApp? =
    when (this) {
        AppDetailUiState.Idle -> null
        is AppDetailUiState.WaitingForBackend -> app
        is AppDetailUiState.Loading -> app
        is AppDetailUiState.Ready -> app
        is AppDetailUiState.Failure -> app
    }

private fun AppOpModeChangeRequest.matches(item: AppOpDisplayItem): Boolean =
    scope == item.scope &&
        operationName.equals(item.operationName, ignoreCase = true)

private val DetailContentPadding = PaddingValues(
    start = 20.dp,
    top = 12.dp,
    end = 20.dp,
    bottom = 24.dp,
)
private const val SKELETON_ROW_COUNT = 5
private const val SKELETON_ALPHA = 0.55f
