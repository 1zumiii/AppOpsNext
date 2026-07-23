package dev.izumi.appopsnext.presentation.templates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.presentation.app_detail.AppOpDisplayCatalog
import dev.izumi.appopsnext.presentation.app_detail.KnownAppOp
import dev.izumi.appopsnext.templates.model.PermissionTemplate
import dev.izumi.appopsnext.templates.model.PermissionTemplateRule
import kotlin.math.abs
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    uiState: TemplatesUiState,
    onCreateTemplate: (String) -> Unit,
    onSelectTemplate: (String) -> Unit,
    onCloseEditor: () -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onRuleModeChange: (String, AppOpMode) -> Unit,
    onRuleScopeChange: (String, AppOpScope) -> Unit,
    onAddRule: (String) -> Unit,
    onRemoveRule: (String) -> Unit,
    onRuleOrderChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
) {
    val selectedTemplate = uiState.selectedTemplate
    var showCreateDialog by remember { mutableStateOf(false) }
    var deleteCandidate by remember {
        mutableStateOf<PermissionTemplate?>(null)
    }

    BackHandler(enabled = selectedTemplate != null, onBack = onCloseEditor)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = selectedTemplate?.name
                            ?: stringResource(R.string.templates_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    if (selectedTemplate != null) {
                        IconButton(onClick = onCloseEditor) {
                            Icon(
                                painter = painterResource(
                                    R.drawable.ic_arrow_back,
                                ),
                                contentDescription = stringResource(
                                    R.string.action_back,
                                ),
                            )
                        }
                    }
                },
                actions = {
                    if (selectedTemplate == null) {
                        TextButton(onClick = { showCreateDialog = true }) {
                            Text(text = stringResource(R.string.template_create))
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
        if (selectedTemplate == null) {
            TemplateList(
                templates = uiState.templates,
                onSelectTemplate = onSelectTemplate,
                onDeleteTemplate = { deleteCandidate = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )
        } else {
            TemplateEditor(
                template = selectedTemplate,
                onRuleModeChange = onRuleModeChange,
                onRuleScopeChange = onRuleScopeChange,
                onAddRule = onAddRule,
                onRemoveRule = onRemoveRule,
                onRuleOrderChange = onRuleOrderChange,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )
        }
    }

    if (showCreateDialog) {
        CreateTemplateDialog(
            onCreate = { name ->
                showCreateDialog = false
                onCreateTemplate(name)
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    deleteCandidate?.let { template ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = {
                Text(text = stringResource(R.string.template_delete_title))
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.template_delete_message,
                        template.name,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteCandidate = null
                        onDeleteTemplate(template.id)
                    },
                ) {
                    Text(text = stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun TemplateList(
    templates: List<PermissionTemplate>,
    onSelectTemplate: (String) -> Unit,
    onDeleteTemplate: (PermissionTemplate) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (templates.isEmpty()) {
        Column(
            modifier = modifier.padding(32.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.templates_empty),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.templates_empty_detail),
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(templates, key = PermissionTemplate::id) { template ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectTemplate(template.id) },
                colors = CardDefaults.cardColors(
                    containerColor =
                        MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = template.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(
                            R.string.template_rule_count,
                            template.rules.size,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { onDeleteTemplate(template) }) {
                            Text(text = stringResource(R.string.action_delete))
                        }
                        TextButton(onClick = {
                            onSelectTemplate(template.id)
                        }) {
                            Text(text = stringResource(R.string.action_edit))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateEditor(
    template: PermissionTemplate,
    onRuleModeChange: (String, AppOpMode) -> Unit,
    onRuleScopeChange: (String, AppOpScope) -> Unit,
    onAddRule: (String) -> Unit,
    onRemoveRule: (String) -> Unit,
    onRuleOrderChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPermissionPicker by remember(template.id) {
        mutableStateOf(false)
    }
    var displayedRules by remember(template.id) {
        mutableStateOf(template.rules)
    }
    var draggedOperationName by remember(template.id) {
        mutableStateOf<String?>(null)
    }
    var draggedOffset by remember(template.id) {
        mutableStateOf(0f)
    }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val knownOperations = remember { AppOpDisplayCatalog.knownOperations() }
    val knownByStableName = remember(knownOperations) {
        knownOperations.associateBy(KnownAppOp::stableName)
    }
    LaunchedEffect(template.rules, draggedOperationName) {
        if (draggedOperationName == null) {
            displayedRules = template.rules
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.pointerInput(template.id) {
            detectDragGesturesAfterLongPress(
                onDragStart = { pointerOffset ->
                    val item = listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { itemInfo ->
                            pointerOffset.y.toInt() in
                                itemInfo.offset until
                                (itemInfo.offset + itemInfo.size) &&
                                itemInfo.key
                                    .toString()
                                    .startsWith(RULE_ITEM_KEY_PREFIX)
                        }
                    draggedOperationName = item
                        ?.key
                        ?.toString()
                        ?.removePrefix(RULE_ITEM_KEY_PREFIX)
                    draggedOffset = 0f
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    val operationName =
                        draggedOperationName ?: return@detectDragGesturesAfterLongPress
                    draggedOffset += dragAmount.y
                    val currentItem = listState.layoutInfo.visibleItemsInfo
                        .firstOrNull {
                            it.key == ruleItemKey(operationName)
                        } ?: return@detectDragGesturesAfterLongPress
                    val draggedCenter = currentItem.offset +
                        currentItem.size / 2f +
                        draggedOffset
                    val targetItem = listState.layoutInfo.visibleItemsInfo
                        .filter {
                            it.key
                                .toString()
                                .startsWith(RULE_ITEM_KEY_PREFIX)
                        }
                        .minByOrNull {
                            abs(
                                draggedCenter -
                                    (it.offset + it.size / 2f),
                            )
                        }
                    if (
                        targetItem != null &&
                        targetItem.key != currentItem.key
                    ) {
                        val targetOperation = targetItem.key
                            .toString()
                            .removePrefix(RULE_ITEM_KEY_PREFIX)
                        val fromIndex = displayedRules.indexOfFirst {
                            it.stableOperationName == operationName
                        }
                        val toIndex = displayedRules.indexOfFirst {
                            it.stableOperationName == targetOperation
                        }
                        if (fromIndex >= 0 && toIndex >= 0) {
                            displayedRules =
                                displayedRules.toMutableList().apply {
                                    add(toIndex, removeAt(fromIndex))
                                }
                            draggedOffset +=
                                currentItem.offset - targetItem.offset
                        }
                    }
                    val scrollAmount = when {
                        draggedCenter <
                            listState.layoutInfo.viewportStartOffset + 96 ->
                            -24f

                        draggedCenter >
                            listState.layoutInfo.viewportEndOffset - 96 ->
                            24f

                        else -> 0f
                    }
                    if (scrollAmount != 0f) {
                        coroutineScope.launch {
                            listState.scrollBy(scrollAmount)
                        }
                    }
                },
                onDragEnd = {
                    onRuleOrderChange(
                        displayedRules.map(
                            PermissionTemplateRule::stableOperationName,
                        ),
                    )
                    draggedOperationName = null
                    draggedOffset = 0f
                },
                onDragCancel = {
                    displayedRules = template.rules
                    draggedOperationName = null
                    draggedOffset = 0f
                },
            )
        },
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.template_editor_detail),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text = stringResource(R.string.template_reorder_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        itemsIndexed(
            items = displayedRules,
            key = { _, rule -> ruleItemKey(rule.stableOperationName) },
        ) { _, rule ->
            val isDragging =
                rule.stableOperationName == draggedOperationName
            TemplateRuleItem(
                rule = rule,
                knownOperation = knownByStableName[rule.stableOperationName],
                onModeChange = onRuleModeChange,
                onScopeChange = onRuleScopeChange,
                onRemove = onRemoveRule,
                isDragging = isDragging,
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragging) {
                            draggedOffset
                        } else {
                            0f
                        }
                    },
            )
            HorizontalDivider()
        }
        item {
            FilledTonalButton(
                onClick = { showPermissionPicker = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text(text = stringResource(R.string.template_add_permission))
            }
        }
    }

    if (showPermissionPicker) {
        PermissionPickerDialog(
            operations = knownOperations.filterNot { operation ->
                displayedRules.any {
                    it.stableOperationName == operation.stableName
                }
            },
            onSelect = { operation ->
                onAddRule(operation.stableName)
                showPermissionPicker = false
            },
            onDismiss = { showPermissionPicker = false },
        )
    }
}

@Composable
private fun TemplateRuleItem(
    rule: PermissionTemplateRule,
    knownOperation: KnownAppOp?,
    onModeChange: (String, AppOpMode) -> Unit,
    onScopeChange: (String, AppOpScope) -> Unit,
    onRemove: (String) -> Unit,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                Color.Transparent
            },
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 8.dp else 0.dp,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = knownOperation?.let {
                        stringResource(it.labelRes)
                    } ?: rule.stableOperationName,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.titleMedium,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_drag_handle),
                    contentDescription = stringResource(
                        R.string.template_reorder_action,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = rule.stableOperationName,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            TemplateSettingRow(
                title = stringResource(R.string.template_mode_title),
                description = stringResource(R.string.template_mode_detail),
            ) {
                ModeMenu(
                    mode = rule.mode,
                    onModeChange = {
                        onModeChange(rule.stableOperationName, it)
                    },
                )
            }
            TemplateSettingRow(
                title = stringResource(R.string.template_scope_title),
                description = stringResource(R.string.template_scope_detail),
            ) {
                ScopeMenu(
                    scope = rule.scope,
                    onScopeChange = {
                        onScopeChange(rule.stableOperationName, it)
                    },
                )
            }
            TextButton(
                onClick = {
                    onRemove(rule.stableOperationName)
                },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(text = stringResource(R.string.action_remove))
            }
        }
    }
}

private const val RULE_ITEM_KEY_PREFIX = "template-rule:"

private fun ruleItemKey(operationName: String): String =
    "$RULE_ITEM_KEY_PREFIX$operationName"

@Composable
private fun TemplateSettingRow(
    title: String,
    description: String,
    menu: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        menu()
    }
}

@Composable
private fun ModeMenu(
    mode: AppOpMode,
    onModeChange: (AppOpMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
        ) {
            Text(
                text = stringResource(
                    R.string.template_dropdown_button,
                    modeLabel(mode),
                ),
                fontWeight = FontWeight.SemiBold,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            AppOpMode.entries.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text(text = modeLabel(candidate)) },
                    enabled = candidate != mode,
                    onClick = {
                        expanded = false
                        onModeChange(candidate)
                    },
                )
            }
        }
    }
}

@Composable
private fun ScopeMenu(
    scope: AppOpScope,
    onScopeChange: (AppOpScope) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
        ) {
            Text(
                text = stringResource(
                    R.string.template_dropdown_button,
                    scopeLabel(scope),
                ),
                fontWeight = FontWeight.SemiBold,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            AppOpScope.entries.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text(text = scopeLabel(candidate)) },
                    enabled = candidate != scope,
                    onClick = {
                        expanded = false
                        onScopeChange(candidate)
                    },
                )
            }
        }
    }
}

@Composable
private fun CreateTemplateDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.template_create_title))
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = {
                    Text(text = stringResource(R.string.template_name))
                },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank(),
            ) {
                Text(text = stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun PermissionPickerDialog(
    operations: List<KnownAppOp>,
    onSelect: (KnownAppOp) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current
    val filteredOperations = operations.filter { operation ->
        query.isBlank() ||
            operation.shellName.contains(query, ignoreCase = true) ||
            operation.stableName.contains(query, ignoreCase = true) ||
            context.getString(operation.labelRes).contains(
                query,
                ignoreCase = true,
            )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.template_add_permission))
        },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = {
                        Text(
                            text = stringResource(
                                R.string.template_permission_search,
                            ),
                        )
                    },
                    singleLine = true,
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .padding(top = 8.dp),
                ) {
                    items(
                        filteredOperations,
                        key = KnownAppOp::stableName,
                    ) { operation ->
                        ListItem(
                            modifier = Modifier.clickable {
                                onSelect(operation)
                            },
                            headlineContent = {
                                Text(text = stringResource(operation.labelRes))
                            },
                            supportingContent = {
                                Text(text = operation.stableName)
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun modeLabel(mode: AppOpMode): String =
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
private fun scopeLabel(scope: AppOpScope): String =
    stringResource(
        when (scope) {
            AppOpScope.PACKAGE -> R.string.app_detail_scope_package
            AppOpScope.UID -> R.string.app_detail_scope_uid
        },
    )
