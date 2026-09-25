package dev.izumi.appopsnext.presentation.templates

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.presentation.components.AppBottomSheet
import dev.izumi.appopsnext.presentation.components.CompactSearchField
import dev.izumi.appopsnext.presentation.components.CompactTextField
import dev.izumi.appopsnext.ui.theme.mainPageHeadingWeight
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.presentation.app_detail.AppOpDisplayCatalog
import dev.izumi.appopsnext.presentation.app_detail.KnownAppOp
import dev.izumi.appopsnext.presentation.components.MainPageSectionTitle
import dev.izumi.appopsnext.presentation.components.PermissionEntryIcon
import dev.izumi.appopsnext.presentation.components.MainPageEntryIcon
import dev.izumi.appopsnext.templates.model.PermissionTemplate
import dev.izumi.appopsnext.templates.model.PermissionTemplateRule
import dev.izumi.appopsnext.templates.NewAppPolicyTemplate
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
    onRuleSelectionChange: (List<String>) -> Unit,
    onRuleOrderChange: (List<String>) -> Unit,
    onAutoApplyNewAppTemplateChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
) {
    val selectedTemplate = uiState.selectedTemplate
    var showCreateDialog by remember { mutableStateOf(false) }
    var deleteCandidate by remember {
        mutableStateOf<PermissionTemplate?>(null)
    }
    var showNewAppPolicyInfo by remember { mutableStateOf(false) }
    var showTemplateActions by remember(selectedTemplate?.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}
    LaunchedEffect(uiState.autoApplyNewAppTemplate) {
        if (
            uiState.autoApplyNewAppTemplate &&
            context.checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
    }

    BackHandler(enabled = selectedTemplate != null, onBack = onCloseEditor)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = selectedTemplate?.let {
                            templateDisplayName(it)
                        }
                            ?: stringResource(R.string.templates_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = mainPageHeadingWeight(),
                    )
                },
                navigationIcon = {
                    if (selectedTemplate != null) {
                        IconButton(onClick = onCloseEditor) {
                            Icon(
                                painter = painterResource(
                                    R.drawable.ic_ph_arrow_left,
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
                        IconButton(onClick = { showCreateDialog = true }) {
                            Icon(
                                painter = painterResource(
                                    R.drawable.ic_ph_plus,
                                ),
                                contentDescription = stringResource(
                                    R.string.template_create_title,
                                ),
                            )
                        }
                    } else if (
                        NewAppPolicyTemplate.isBuiltIn(selectedTemplate.id)
                    ) {
                        IconButton(
                            onClick = { showNewAppPolicyInfo = true },
                        ) {
                            Icon(
                                painter = painterResource(
                                    R.drawable.ic_ph_info,
                                ),
                                contentDescription = stringResource(
                                    R.string
                                        .template_new_app_policy_information,
                                ),
                            )
                        }
                    } else {
                        Box {
                            IconButton(onClick = { showTemplateActions = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_ph_dots_three),
                                    contentDescription = stringResource(R.string.template_more_options),
                                )
                            }
                            DropdownMenu(
                                expanded = showTemplateActions,
                                onDismissRequest = { showTemplateActions = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.action_delete)) },
                                    onClick = {
                                        showTemplateActions = false
                                        deleteCandidate = selectedTemplate
                                    },
                                )
                            }
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
                autoApplyNewAppTemplate =
                    uiState.autoApplyNewAppTemplate,
                onAutoApplyNewAppTemplateChange =
                    onAutoApplyNewAppTemplateChange,
                onSelectTemplate = onSelectTemplate,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )
        } else {
            TemplateEditor(
                template = selectedTemplate,
                onRuleModeChange = onRuleModeChange,
                onRuleSelectionChange = onRuleSelectionChange,
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
        AppBottomSheet(
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
                Button(
                    onClick = {
                        deleteCandidate = null
                        onDeleteTemplate(template.id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(text = stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteCandidate = null }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showNewAppPolicyInfo) {
        AppBottomSheet(
            onDismissRequest = { showNewAppPolicyInfo = false },
            title = {
                Text(
                    text = stringResource(
                        R.string.template_new_app_policy_title,
                    ),
                )
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.template_new_app_policy_description,
                    ),
                )
            },
            confirmButton = {
                Button(
                    onClick = { showNewAppPolicyInfo = false },
                ) {
                    Text(text = stringResource(R.string.action_got_it))
                }
            },
        )
    }
}

@Composable
private fun TemplateList(
    templates: List<PermissionTemplate>,
    autoApplyNewAppTemplate: Boolean,
    onAutoApplyNewAppTemplateChange: (Boolean) -> Unit,
    onSelectTemplate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (templates.isEmpty()) {
        Column(
            modifier = modifier,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(32.dp),
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
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val newAppTemplate = templates.firstOrNull {
            NewAppPolicyTemplate.isBuiltIn(it.id)
        }
        val customTemplates = templates.filterNot {
            NewAppPolicyTemplate.isBuiltIn(it.id)
        }
        newAppTemplate?.let { template ->
            item(key = template.id) {
                NewAppPolicyCard(
                    template = template,
                    enabled = autoApplyNewAppTemplate,
                    onEnabledChange = onAutoApplyNewAppTemplateChange,
                    onEdit = { onSelectTemplate(template.id) },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
        item(key = CUSTOM_TEMPLATE_DIVIDER_KEY) {
            MainPageSectionTitle(
                text = stringResource(R.string.templates_custom_section),
            )
        }
        if (customTemplates.isEmpty()) {
            item(key = "templates-custom-empty") {
                Text(
                    text = stringResource(R.string.templates_custom_empty),
                    modifier = Modifier.padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(customTemplates, key = PermissionTemplate::id) { template ->
            CustomTemplateCard(
                template = template,
                onEdit = { onSelectTemplate(template.id) },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

@Composable
private fun NewAppPolicyCard(
    template: PermissionTemplate,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MainPageEntryIcon(
                    iconRes = R.drawable.ic_ph_file_text,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = templateDisplayName(template),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.template_rule_count, template.rules.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        painter = painterResource(R.drawable.ic_ph_pencil_simple),
                        contentDescription = stringResource(R.string.action_edit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TemplateRulePreview(
                template = template,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 56.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.template_new_app_auto_apply),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                )
            }
        }
    }
}

@Composable
private fun CustomTemplateCard(
    template: PermissionTemplate,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onEdit),
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
            MainPageEntryIcon(
                iconRes = R.drawable.ic_ph_note_pencil,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = templateDisplayName(template),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TemplateRulePreview(
                    template = template,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    text = stringResource(R.string.template_rule_count_compact, template.rules.size),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TemplateRulePreview(
    template: PermissionTemplate,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (template.rules.isEmpty()) return
    val namesByOperation = remember {
        AppOpDisplayCatalog.knownOperations().associateBy(KnownAppOp::stableName)
    }
    val names = template.rules.take(3).map { rule ->
        namesByOperation[rule.stableOperationName]?.let {
            stringResource(it.labelRes)
        } ?: rule.stableOperationName
    }
    val style = MaterialTheme.typography.bodySmall
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth().padding(top = 4.dp)) {
        val availableWidth = with(density) { maxWidth.roundToPx() }
        val preview = (names.size downTo 1).firstNotNullOfOrNull { visibleCount ->
            val visibleNames = names.take(visibleCount).joinToString(" · ")
            val hiddenCount = template.rules.size - visibleCount
            val candidate = if (hiddenCount > 0) {
                stringResource(R.string.template_preview_more, visibleNames, hiddenCount)
            } else {
                visibleNames
            }
            candidate.takeIf {
                textMeasurer.measure(
                    text = it,
                    style = style,
                    maxLines = 1,
                    softWrap = false,
                ).size.width <= availableWidth
            }
        } ?: if (template.rules.size == 1) {
            names.first()
        } else {
            stringResource(R.string.template_preview_count_only, template.rules.size)
        }
        Text(
            text = preview,
            color = color,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun templateDisplayName(template: PermissionTemplate): String =
    if (NewAppPolicyTemplate.isBuiltIn(template.id)) {
        stringResource(R.string.template_new_app_policy_title)
    } else {
        template.name
    }

@Composable
private fun TemplateEditor(
    template: PermissionTemplate,
    onRuleModeChange: (String, AppOpMode) -> Unit,
    onRuleSelectionChange: (List<String>) -> Unit,
    onRuleOrderChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPermissionManager by remember(template.id) {
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
            MainPageSectionTitle(
                text = stringResource(R.string.template_rule_count, displayedRules.size),
                horizontalPadding = 0.dp,
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
        }
        item {
            FilledTonalButton(
                onClick = { showPermissionManager = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.template_manage_permissions,
                    ),
                )
            }
        }
    }

    if (showPermissionManager) {
        PermissionManagerDialog(
            operations = knownOperations,
            currentRules = displayedRules,
            onConfirm = { operationNames ->
                onRuleSelectionChange(operationNames)
                showPermissionManager = false
            },
            onDismiss = { showPermissionManager = false },
        )
    }
}

@Composable
private fun TemplateRuleItem(
    rule: PermissionTemplateRule,
    knownOperation: KnownAppOp?,
    onModeChange: (String, AppOpMode) -> Unit,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PermissionEntryIcon(rule.stableOperationName)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = knownOperation?.let {
                            stringResource(it.labelRes)
                        } ?: rule.stableOperationName,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (knownOperation != null) {
                        Text(
                            text = rule.stableOperationName,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Icon(
                    painter = painterResource(R.drawable.ic_ph_dots_six_vertical),
                    contentDescription = stringResource(
                        R.string.template_reorder_action,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            TemplateSettingRow(
                title = stringResource(R.string.template_mode_title),
            ) {
                ModeMenu(
                    mode = rule.mode,
                    onModeChange = {
                        onModeChange(rule.stableOperationName, it)
                    },
                )
            }
        }
    }
}

private const val RULE_ITEM_KEY_PREFIX = "template-rule:"
private const val CUSTOM_TEMPLATE_DIVIDER_KEY = "custom-template-divider"

private fun ruleItemKey(operationName: String): String =
    "$RULE_ITEM_KEY_PREFIX$operationName"

@Composable
private fun TemplateSettingRow(
    title: String,
    menu: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium,
        )
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
private fun CreateTemplateDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AppBottomSheet(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.template_create_title))
        },
        text = {
            CompactTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.template_name),
            )
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank(),
            ) {
                Text(text = stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun PermissionManagerDialog(
    operations: List<KnownAppOp>,
    currentRules: List<PermissionTemplateRule>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current
    val options = remember(operations, currentRules) {
        val knownNames = operations.mapTo(mutableSetOf()) {
            it.stableName
        }
        buildList {
            operations.forEach { operation ->
                add(
                    TemplatePermissionOption(
                        stableName = operation.stableName,
                        knownOperation = operation,
                    ),
                )
            }
            currentRules.forEach { rule ->
                if (rule.stableOperationName !in knownNames) {
                    add(
                        TemplatePermissionOption(
                            stableName = rule.stableOperationName,
                            knownOperation = null,
                        ),
                    )
                    knownNames += rule.stableOperationName
                }
            }
        }
    }
    var selectedNames by remember(currentRules) {
        mutableStateOf(
            currentRules
                .map(PermissionTemplateRule::stableOperationName)
                .toSet(),
        )
    }
    val filteredOperations = options.filter { option ->
        query.isBlank() ||
            option.stableName.contains(query, ignoreCase = true) ||
            option.knownOperation?.let { operation ->
                operation.shellName.contains(query, ignoreCase = true) ||
                    context.getString(operation.labelRes).contains(
                        query,
                        ignoreCase = true,
                    )
            } == true
    }
    val toggleSelection = { operationName: String, selected: Boolean ->
        selectedNames = if (selected) {
            selectedNames + operationName
        } else {
            selectedNames - operationName
        }
    }
    AppBottomSheet(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.template_manage_permissions))
        },
        text = {
            Column {
                CompactSearchField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.template_permission_search),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .padding(top = 8.dp),
                ) {
                    items(
                        filteredOperations,
                        key = TemplatePermissionOption::stableName,
                    ) { option ->
                        val selected = option.stableName in selectedNames
                        ListItem(
                            modifier = Modifier.clickable {
                                toggleSelection(option.stableName, !selected)
                            },
                            leadingContent = {
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = { checked ->
                                        toggleSelection(
                                            option.stableName,
                                            checked,
                                        )
                                    },
                                )
                            },
                            headlineContent = {
                                Text(
                                    text = option.knownOperation?.let {
                                        stringResource(it.labelRes)
                                    } ?: option.stableName,
                                )
                            },
                            supportingContent = option.knownOperation?.let {
                                {
                                    Text(text = option.stableName)
                                }
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
            Button(
                onClick = {
                    val currentOrder = currentRules
                        .map(PermissionTemplateRule::stableOperationName)
                        .filter(selectedNames::contains)
                    val currentNames = currentOrder.toSet()
                    val additions = options
                        .map(TemplatePermissionOption::stableName)
                        .filter { operationName ->
                            operationName in selectedNames &&
                                operationName !in currentNames
                        }
                    onConfirm(currentOrder + additions)
                },
            ) {
                Text(text = stringResource(R.string.action_apply))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

private data class TemplatePermissionOption(
    val stableName: String,
    val knownOperation: KnownAppOp?,
)

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
