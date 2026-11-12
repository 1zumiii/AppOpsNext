package dev.izumi.appopsnext.presentation.experimental

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.apps.AppSortOrder
import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.monitor.MonitorOpCodes
import dev.izumi.appopsnext.monitor.VendorMonitorOps
import dev.izumi.appopsnext.monitor.BuiltInMonitorTemplates
import dev.izumi.appopsnext.monitor.MonitorOutcomes
import dev.izumi.appopsnext.monitor.MonitorTemplate
import dev.izumi.appopsnext.monitor.MonitorTemplateApplier
import dev.izumi.appopsnext.monitor.MonitorTemplateRule
import dev.izumi.appopsnext.presentation.app_detail.appOpDisplayLabel
import dev.izumi.appopsnext.presentation.batch.StringSelectionSaver
import dev.izumi.appopsnext.presentation.components.AppBottomSheet
import dev.izumi.appopsnext.presentation.components.CompactTextField
import dev.izumi.appopsnext.presentation.components.MainPageChevron

/**
 * Monitor templates: the built-in ones, the user's own, and the advice that names
 * no permissions and so cannot be applied.
 *
 * The page holds its own steps (editing a template, one of its rules, its
 * permissions, and choosing the apps to apply it to) so the root screen only has
 * one route for all of them. Viewing and editing stay open while the monitor
 * runs, because a template changes nothing until it is applied. Applying does
 * change the watched apps and operations, which are locked while it runs.
 */
@Composable
fun MonitorTemplatesScreen(
    viewModel: MonitorTemplatesViewModel,
    uiState: ExperimentalUiState,
    apps: List<InstalledApp>,
    sortOrder: AppSortOrder,
    onSortOrderChange: (AppSortOrder) -> Unit,
    onShowAllOperationsChange: (Boolean) -> Unit,
    onSuppressAllOperationsWarning: () -> Unit,
    onBack: () -> Unit,
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val outcome by viewModel.applyOutcome.collectAsStateWithLifecycle()
    val vendorCodes by VendorMonitorOps.codes.collectAsStateWithLifecycle()
    // Whether a private operation counts as available depends on the device,
    // which is asked only when some template names one.
    val namesVendorOps = templates.any { template -> template.operationNames.any(MonitorOpCodes::isVendor) }
    LaunchedEffect(namesVendorOps) {
        if (namesVendorOps) viewModel.probeVendorOps()
    }
    val locked = uiState.monitorEnabled || uiState.monitorBusy

    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var ruleOperation by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingOperations by rememberSaveable { mutableStateOf(false) }
    var applyingId by rememberSaveable { mutableStateOf<String?>(null) }
    var picked by rememberSaveable(stateSaver = StringSelectionSaver) {
        mutableStateOf(emptySet<String>())
    }
    var confirming by rememberSaveable { mutableStateOf(false) }

    val editing = editingId?.let { id -> templates.firstOrNull { it.id == id } }
    val applying = applyingId?.let { id ->
        BuiltInMonitorTemplates.find(id) ?: templates.firstOrNull { it.id == id }
    }
    val closeApply = {
        applyingId = null
        picked = emptySet()
        confirming = false
    }
    val startApply = { template: MonitorTemplate ->
        picked = emptySet()
        applyingId = template.id
    }

    BackHandler(
        enabled = applyingId != null || ruleOperation != null || pickingOperations || editingId != null,
    ) {
        when {
            applyingId != null -> closeApply()
            ruleOperation != null -> ruleOperation = null
            pickingOperations -> pickingOperations = false
            else -> editingId = null
        }
    }

    val rule = ruleOperation?.let { operation ->
        editing?.rules?.firstOrNull { it.operationName == operation }
    }
    when {
        applying != null -> MonitorTargetsScreen(
            apps = apps,
            selectionByPackage = uiState.selectionByPackage,
            sortOrder = sortOrder,
            onSortOrderChange = onSortOrderChange,
            onBack = closeApply,
            onAppSelected = {},
            title = monitorTemplateName(applying),
            hint = stringResource(R.string.monitor_template_apps_hint),
            picked = picked,
            onPickedChange = { picked = it },
            pickActions = {
                Button(
                    onClick = { confirming = true },
                    enabled = picked.isNotEmpty() && !locked,
                ) {
                    Text(text = stringResource(R.string.monitor_template_apply))
                }
            },
        )

        editing != null && rule != null -> MonitorTemplateRuleScreen(
            template = editing,
            rule = rule,
            onBack = { ruleOperation = null },
            onThrottleChange = { viewModel.setThrottle(editing.id, rule.operationName, it) },
            onHeadsUpChange = { viewModel.setHeadsUp(editing.id, rule.operationName, it) },
            onOutcomesChange = { viewModel.setOutcomes(editing.id, rule.operationName, it) },
            onBackgroundOnlyChange = {
                viewModel.setBackgroundOnly(editing.id, rule.operationName, it)
            },
        )

        editing != null && pickingOperations -> MonitorOperationsScreen(
            title = editing.name,
            stateKey = "template:${editing.id}",
            hint = stringResource(R.string.monitor_template_operations_hint),
            selected = editing.operationNames.toSet(),
            showAll = uiState.showAllOperations,
            warningSuppressed = uiState.allOperationsWarningSuppressed,
            onBack = { pickingOperations = false },
            onSelectionChange = { viewModel.setOperations(editing.id, it) },
            onShowAllChange = onShowAllOperationsChange,
            onSuppressWarning = onSuppressAllOperationsWarning,
        )

        editing != null -> MonitorTemplateEditor(
            template = editing,
            locked = locked,
            onBack = { editingId = null },
            onRename = { viewModel.rename(editing.id, it) },
            onDelete = {
                editingId = null
                viewModel.delete(editing.id)
            },
            onChooseOperations = { pickingOperations = true },
            onOpenRule = { ruleOperation = it.operationName },
            onApply = { startApply(editing) },
        )

        // A template just created is written in the background, so its id can be
        // open a moment before the stored list has it.
        editingId != null -> Box(modifier = Modifier.fillMaxSize())

        else -> MonitorTemplateList(
            templates = templates,
            locked = locked,
            onBack = onBack,
            onCreate = { name -> editingId = viewModel.create(name) },
            onOpen = { editingId = it.id },
            onApply = startApply,
        )
    }

    if (confirming && applying != null) {
        val packages = picked
        val preview = MonitorTemplateApplier.apply(
            template = applying,
            packageNames = packages,
            targets = uiState.targets,
            points = uiState.points,
            isAvailable = { MonitorOpCodes.codeOf(it, vendorCodes) != null },
        )
        AppBottomSheet(
            onDismissRequest = { confirming = false },
            title = { Text(text = stringResource(R.string.monitor_template_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = stringResource(R.string.monitor_template_count_apps, preview.packageNames.size))
                    if (preview.appliedOperations.isEmpty()) {
                        Text(text = stringResource(R.string.monitor_template_nothing_to_apply))
                    } else {
                        Text(
                            text = stringResource(
                                R.string.monitor_template_confirm_operations,
                                preview.appliedOperations.size,
                            ),
                        )
                        // What each point becomes, in the words the editor and the
                        // points list use. A long template scrolls here rather than
                        // pushing the buttons off the sheet.
                        val rules = applying.rules
                            .distinctBy(MonitorTemplateRule::operationName)
                            .filter { it.operationName in preview.appliedOperations }
                        Column(
                            modifier = Modifier
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            rules.forEach { rule ->
                                Text(
                                    text = stringResource(
                                        R.string.template_rule_summary,
                                        operationLabel(rule.operationName),
                                        monitorPointSummary(rule.settingsFor("")),
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (rules.any(MonitorTemplateRule::isDefault)) {
                            Text(text = stringResource(R.string.monitor_template_confirm_defaults))
                        }
                    }
                    if (preview.skippedOperations.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.monitor_template_skipped,
                                operationLabels(preview.skippedOperations),
                            ),
                        )
                    }
                    Text(text = stringResource(R.string.monitor_template_confirm_replace))
                    if (locked) {
                        Text(
                            text = stringResource(R.string.monitor_templates_locked),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.apply(applying, packages)
                        closeApply()
                    },
                    enabled = !locked && preview.appliedOperations.isNotEmpty() &&
                        preview.packageNames.isNotEmpty(),
                ) {
                    Text(text = stringResource(R.string.monitor_template_apply))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirming = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }

    outcome?.let { result ->
        ApplyOutcomeSheet(outcome = result, onDismiss = viewModel::dismissApplyOutcome)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonitorTemplateList(
    templates: List<MonitorTemplate>,
    locked: Boolean,
    onBack: () -> Unit,
    onCreate: (String) -> Unit,
    onOpen: (MonitorTemplate) -> Unit,
    onApply: (MonitorTemplate) -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.monitor_templates_title),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = { showDetails = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ph_info),
                            contentDescription = stringResource(R.string.monitor_templates_details_title),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.monitor_templates_hint),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (locked) {
                item { LockedNotice() }
            }
            item { ExperimentalSectionTitle(stringResource(R.string.monitor_templates_builtin_header)) }
            items(BuiltInMonitorTemplates.all, key = MonitorTemplate::id) { template ->
                val example = builtInExamples.getValue(template.id)
                ExampleCard(
                    example = example,
                    operations = template.operationNames,
                    action = {
                        FilledTonalButton(onClick = { onApply(template) }, enabled = !locked) {
                            Text(text = stringResource(R.string.monitor_template_apply))
                        }
                    },
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        ExperimentalSectionTitle(stringResource(R.string.monitor_templates_custom_header))
                    }
                    TextButton(
                        onClick = { creating = true },
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ph_plus),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(text = stringResource(R.string.monitor_template_create))
                    }
                }
            }
            if (templates.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.monitor_templates_empty),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(templates, key = MonitorTemplate::id) { template ->
                ExperimentalListCard(
                    modifier = Modifier.clickable { onOpen(template) },
                    leadingContent = { ExperimentalRowIcon(R.drawable.ic_ph_note_pencil) },
                    headlineContent = {
                        Text(text = template.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Column {
                            Text(
                                text = if (template.rules.isEmpty()) {
                                    stringResource(R.string.monitor_template_needs_rules)
                                } else {
                                    operationLabels(template.operationNames)
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // The button takes its own tap, so the rest of the card
                            // still opens the editor.
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                FilledTonalButton(
                                    onClick = { onApply(template) },
                                    enabled = !locked && template.rules.isNotEmpty(),
                                ) {
                                    Text(text = stringResource(R.string.monitor_template_apply))
                                }
                            }
                        }
                    },
                    trailingContent = { MainPageChevron() },
                )
            }
            item { ExperimentalSectionTitle(stringResource(R.string.monitor_templates_advice_header)) }
            item {
                Text(
                    text = stringResource(R.string.monitor_templates_advice_hint),
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(adviceExamples, key = MonitorExample::titleRes) { example ->
                ExampleCard(example = example, operations = emptyList(), action = null)
            }
        }
    }

    if (creating) {
        TemplateNameSheet(
            title = stringResource(R.string.monitor_template_create_title),
            initialName = "",
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = { name ->
                creating = false
                onCreate(name)
            },
            onDismiss = { creating = false },
        )
    }

    if (showDetails) {
        AppBottomSheet(
            onDismissRequest = { showDetails = false },
            title = { Text(text = stringResource(R.string.monitor_templates_details_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = stringResource(R.string.monitor_templates_details_add))
                    Text(text = stringResource(R.string.monitor_templates_details_replace))
                    Text(text = stringResource(R.string.monitor_templates_details_skip))
                    Text(text = stringResource(R.string.monitor_templates_details_lock))
                }
            },
            confirmButton = {
                Button(onClick = { showDetails = false }) {
                    Text(text = stringResource(R.string.action_got_it))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonitorTemplateEditor(
    template: MonitorTemplate,
    locked: Boolean,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onChooseOperations: () -> Unit,
    onOpenRule: (MonitorTemplateRule) -> Unit,
    onApply: () -> Unit,
) {
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val vendorCodes by VendorMonitorOps.codes.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = template.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = { renaming = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ph_pencil_simple),
                            contentDescription = stringResource(R.string.monitor_template_rename),
                        )
                    }
                    IconButton(onClick = { deleting = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ph_trash),
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (locked) LockedNotice(horizontalPadding = 0.dp)
                Button(
                    onClick = onApply,
                    enabled = !locked && template.rules.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.monitor_template_apply))
                }
            }
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.monitor_template_editor_hint),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        ExperimentalSectionTitle(stringResource(R.string.monitor_template_rules_header))
                    }
                    TextButton(
                        onClick = onChooseOperations,
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Text(text = stringResource(R.string.monitor_template_choose_operations))
                    }
                }
            }
            if (template.rules.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.template_no_permissions),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item {
                    ExperimentalGroup {
                        template.rules.forEachIndexed { index, rule ->
                            val available = MonitorOpCodes.codeOf(rule.operationName, vendorCodes) != null
                            ExperimentalGroupRow(
                                modifier = Modifier.clickable { onOpenRule(rule) },
                                headlineContent = {
                                    Text(
                                        text = operationLabel(rule.operationName),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = if (available) {
                                            monitorPointSummary(rule.settingsFor(""))
                                        } else {
                                            stringResource(R.string.monitor_template_unavailable)
                                        },
                                        color = if (available) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        },
                                    )
                                },
                                trailingContent = { MainPageChevron() },
                            )
                            if (index < template.rules.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (renaming) {
        TemplateNameSheet(
            title = stringResource(R.string.monitor_template_rename_title),
            initialName = template.name,
            confirmLabel = stringResource(R.string.monitor_template_rename),
            onConfirm = { name ->
                renaming = false
                onRename(name)
            },
            onDismiss = { renaming = false },
        )
    }

    if (deleting) {
        AppBottomSheet(
            onDismissRequest = { deleting = false },
            title = { Text(text = stringResource(R.string.template_delete_title)) },
            text = { Text(text = stringResource(R.string.template_delete_message, template.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        deleting = false
                        onDelete()
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
                OutlinedButton(onClick = { deleting = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** One rule of a template, with the same controls a monitoring point's own page has. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonitorTemplateRuleScreen(
    template: MonitorTemplate,
    rule: MonitorTemplateRule,
    onBack: () -> Unit,
    onThrottleChange: (Int?) -> Unit,
    onHeadsUpChange: (Boolean?) -> Unit,
    onOutcomesChange: (MonitorOutcomes) -> Unit,
    onBackgroundOnlyChange: (Boolean) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = template.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = operationLabel(rule.operationName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.monitor_template_rule_hint),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MonitorPointSettingsControls(
                settings = rule.settingsFor(""),
                enabled = true,
                onThrottleChange = onThrottleChange,
                onHeadsUpChange = onHeadsUpChange,
                onOutcomesChange = onOutcomesChange,
                onBackgroundOnlyChange = onBackgroundOnlyChange,
            )
        }
    }
}

@Composable
private fun ApplyOutcomeSheet(outcome: MonitorTemplateApplyOutcome, onDismiss: () -> Unit) {
    val applied = outcome as? MonitorTemplateApplyOutcome.Applied
    val succeeded = applied != null && applied.operations.isNotEmpty() && applied.appCount > 0
    AppBottomSheet(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (succeeded) {
                        R.string.monitor_template_applied_title
                    } else {
                        R.string.monitor_template_not_applied_title
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    applied == null -> Text(text = stringResource(R.string.monitor_templates_locked))
                    succeeded -> {
                        Text(text = stringResource(R.string.monitor_template_count_apps, applied.appCount))
                        Text(
                            text = stringResource(
                                R.string.monitor_template_count_operations,
                                applied.operations.size,
                                operationLabels(applied.operations),
                            ),
                        )
                    }
                    else -> Text(text = stringResource(R.string.monitor_template_nothing_to_apply))
                }
                if (applied != null && applied.skippedOperations.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.monitor_template_skipped,
                            operationLabels(applied.skippedOperations),
                        ),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_dismiss))
            }
        },
    )
}

@Composable
private fun TemplateNameSheet(
    title: String,
    initialName: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    AppBottomSheet(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            CompactTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.template_name),
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(text = confirmLabel)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

/** Why applying is closed, said where the closed control is. */
@Composable
private fun LockedNotice(horizontalPadding: Dp = 16.dp) {
    Text(
        text = stringResource(R.string.monitor_templates_locked),
        modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(
            painter = painterResource(R.drawable.ic_ph_arrow_left),
            contentDescription = stringResource(R.string.action_back),
        )
    }
}

/**
 * A situation, what to do about it, and the settings it comes to. A built-in
 * template's card adds its permissions and the action that applies it.
 */
@Composable
private fun ExampleCard(
    example: MonitorExample,
    operations: List<String>,
    action: (@Composable () -> Unit)?,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(example.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(example.titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = stringResource(example.bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (operations.isNotEmpty()) {
                Text(
                    text = operationLabels(operations),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                example.settingRes.forEach { labelRes ->
                    ExampleSettingChip(stringResource(labelRes))
                }
            }
            action?.let {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    it()
                }
            }
        }
    }
}

@Composable
private fun ExampleSettingChip(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

private data class MonitorExample(
    val titleRes: Int,
    val bodyRes: Int,
    val iconRes: Int,
    val settingRes: List<Int>,
)

/** How each built-in template is presented: the example it was made from. */
private val builtInExamples: Map<String, MonitorExample> = mapOf(
    BuiltInMonitorTemplates.OWN_USE_LOCATION to MonitorExample(
        R.string.monitor_example_own_use,
        R.string.monitor_example_own_use_body,
        R.drawable.ic_ph_map_pin,
        listOf(
            R.string.monitor_point_background_short,
            R.string.monitor_example_every_minute,
            R.string.monitor_alert_silent,
        ),
    ),
    BuiltInMonitorTemplates.QUIET_LOCATION to MonitorExample(
        R.string.monitor_example_quiet_location,
        R.string.monitor_example_quiet_location_body,
        R.drawable.ic_ph_eye,
        listOf(
            R.string.monitor_point_background_short,
            R.string.monitor_example_every_minute,
            R.string.monitor_alert_loud,
        ),
    ),
    BuiltInMonitorTemplates.CAMERA_MICROPHONE to MonitorExample(
        R.string.monitor_example_sensors,
        R.string.monitor_example_sensors_body,
        R.drawable.ic_ph_camera,
        listOf(
            R.string.monitor_point_background_short,
            R.string.monitor_example_no_limit,
            R.string.monitor_alert_loud,
        ),
    ),
    BuiltInMonitorTemplates.PERSONAL_DATA_READS to MonitorExample(
        R.string.monitor_example_one_shot,
        R.string.monitor_example_one_shot_body,
        R.drawable.ic_ph_clipboard_text,
        listOf(
            R.string.monitor_example_no_limit,
            R.string.monitor_example_on_screen_too,
        ),
    ),
)

/** Advice that names no permissions, so there is nothing to apply. */
private val adviceExamples = listOf(
    MonitorExample(
        R.string.monitor_example_refusals,
        R.string.monitor_example_refusals_body,
        R.drawable.ic_ph_shield,
        listOf(
            R.string.monitor_outcome_refused,
            R.string.monitor_example_no_limit,
            R.string.monitor_alert_loud,
        ),
    ),
    MonitorExample(
        R.string.monitor_example_unknown,
        R.string.monitor_example_unknown_body,
        R.drawable.ic_ph_info,
        listOf(
            R.string.monitor_example_every_30_seconds,
            R.string.monitor_alert_silent,
        ),
    ),
)

@Composable
private fun monitorTemplateName(template: MonitorTemplate): String =
    builtInExamples[template.id]?.let { stringResource(it.titleRes) } ?: template.name

@Composable
private fun operationLabel(operationName: String): String =
    appOpDisplayLabel(operationName)

@Composable
private fun operationLabels(operationNames: List<String>): String =
    operationNames
        .map { operationLabel(it) }
        .joinToString(stringResource(R.string.list_separator))
