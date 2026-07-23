package dev.izumi.appopsnext.presentation.app_detail

import android.content.res.Configuration
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpsReadFailureReason
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.presentation.batch.PermissionBatchSelection
import dev.izumi.appopsnext.presentation.batch.TemplatePickerDialog
import dev.izumi.appopsnext.templates.model.PermissionTemplate
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    uiState: AppDetailUiState,
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
    onModeChangeConfirmed: () -> Unit,
    onModeChangeDismissed: () -> Unit,
    templates: List<PermissionTemplate>,
    onTemplateApplyRequested:
        (PermissionTemplate, InstalledApp) -> Unit,
    onPermissionBatchRequested: (
        InstalledApp,
        List<PermissionBatchSelection>,
        AppOpMode,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = uiState.appOrNull()
    var batchSelectionMode by remember(app?.packageName) {
        mutableStateOf(false)
    }
    var selectedBatchKeys by remember(app?.packageName) {
        mutableStateOf(emptySet<String>())
    }
    var selectedBatchMode by remember(app?.packageName) {
        mutableStateOf(AppOpMode.IGNORE)
    }
    var showTemplatePicker by remember(app?.packageName) {
        mutableStateOf(false)
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
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(
                                R.string.action_back,
                            ),
                        )
                    }
                },
                actions = {
                    if (uiState is AppDetailUiState.Ready) {
                        IconButton(onClick = { showTemplatePicker = true }) {
                            Icon(
                                painter = painterResource(
                                    R.drawable.ic_navigation_templates,
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
                                        R.drawable.ic_action_close
                                    } else {
                                        R.drawable.ic_action_batch
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

            is AppDetailUiState.WaitingForBackend -> MessageContent(
                app = uiState.app,
                message = stringResource(R.string.app_detail_waiting_backend),
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
                    onPermissionBatchRequested(
                        uiState.app,
                        selections,
                        selectedBatchMode,
                    )
                    batchSelectionMode = false
                    selectedBatchKeys = emptySet()
                },
                onModeChangeRequested = onModeChangeRequested,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )

            is AppDetailUiState.Failure -> FailureContent(
                app = uiState.app,
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
    val displayItems = AppOpDisplayCatalog.build(
        entries = state.snapshot.entries,
        query = searchQuery,
        labelResolver = context::getString,
        alternateLabelResolver = alternateContext::getString,
    )
    val totalOperationCount = AppOpDisplayCatalog.build(
        entries = state.snapshot.entries,
        query = "",
        labelResolver = context::getString,
        alternateLabelResolver = alternateContext::getString,
    ).size
    LazyColumn(
        modifier = modifier,
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
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        text = stringResource(
                            R.string.app_detail_search_label,
                        ),
                    )
                },
                singleLine = true,
            )
        }
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
        if (batchSelectionMode) {
            item {
                BatchPermissionControls(
                    selectedCount = selectedBatchKeys.size,
                    selectedMode = selectedBatchMode,
                    onModeChange = onBatchModeChange,
                    onApply = {
                        onApplyPermissionBatch(
                            displayItems
                                .filter {
                                    it.batchSelectionKey() in
                                        selectedBatchKeys
                                }
                                .map {
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
        if (displayItems.isEmpty()) {
            item {
                Text(
                    text = stringResource(
                        if (state.snapshot.entries.isEmpty()) {
                            R.string.app_detail_no_operations
                        } else {
                            R.string.app_detail_no_matching_operations
                        },
                    ),
                    modifier = Modifier.padding(vertical = 32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun BatchPermissionControls(
    selectedCount: Int,
    selectedMode: AppOpMode,
    onModeChange: (AppOpMode) -> Unit,
    onApply: () -> Unit,
) {
    var modeMenuExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(
                R.string.batch_selected_count,
                selectedCount,
            ),
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { modeMenuExpanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(
                        R.string.batch_apply_mode_value,
                        batchModeLabel(selectedMode),
                    ),
                )
            }
            DropdownMenu(
                expanded = modeMenuExpanded,
                onDismissRequest = { modeMenuExpanded = false },
            ) {
                AppOpMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(text = batchModeLabel(mode)) },
                        enabled = mode != selectedMode,
                        onClick = {
                            modeMenuExpanded = false
                            onModeChange(mode)
                        },
                    )
                }
            }
        }
        FilledTonalButton(
            onClick = onApply,
            enabled = selectedCount > 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.action_apply))
        }
    }
}

private fun AppOpDisplayItem.batchSelectionKey(): String =
    "${scope.name}:$operationName"

@Composable
private fun batchModeLabel(mode: AppOpMode): String =
    stringResource(
        when (mode) {
            AppOpMode.ALLOW -> R.string.app_op_mode_allow
            AppOpMode.IGNORE -> R.string.app_op_mode_ignore
            AppOpMode.DENY -> R.string.app_op_mode_deny
            AppOpMode.DEFAULT -> R.string.app_op_mode_default
            AppOpMode.FOREGROUND -> R.string.app_op_mode_foreground
        },
    )

@Composable
private fun AppSummaryCard(
    app: InstalledApp,
    operationCount: Int?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodyMedium,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = operationCount?.let {
                    stringResource(
                        R.string.app_detail_operation_count,
                        it,
                    )
                } ?: stringResource(
                    R.string.app_detail_loading_operations,
                ),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.app_detail_effective_scope_note),
                modifier = Modifier.padding(top = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
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
            HorizontalDivider()
        }
    }
}

@Composable
private fun AppOpSkeletonItem() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
private fun MessageContent(
    app: InstalledApp,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = app.packageName,
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = message,
            modifier = Modifier.padding(top = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FailureContent(
    app: InstalledApp,
    reason: AppOpsReadFailureReason,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(
                when (reason) {
                    AppOpsReadFailureReason.BACKEND_UNAVAILABLE ->
                        R.string.status_appops_backend_unavailable_detail

                    AppOpsReadFailureReason.COMMAND_FAILED ->
                        R.string.status_appops_command_failed_detail

                    AppOpsReadFailureReason.COMMAND_TIMED_OUT ->
                        R.string.status_appops_command_timed_out_detail
                },
            ),
        )
        Text(
            text = app.packageName,
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(
            onClick = onRefresh,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(text = stringResource(R.string.action_retry))
        }
    }
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
