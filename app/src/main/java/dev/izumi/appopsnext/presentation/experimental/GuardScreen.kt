package dev.izumi.appopsnext.presentation.experimental

import dev.izumi.appopsnext.presentation.components.PageListBottomPadding
import android.app.Application
import android.text.format.DateUtils
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import dev.izumi.appopsnext.presentation.components.AppBottomSheet
import dev.izumi.appopsnext.presentation.components.CompactTextField
import dev.izumi.appopsnext.presentation.components.MainPageChevron
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import dev.izumi.appopsnext.presentation.components.CompactSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import android.content.pm.PackageManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.izumi.appopsnext.AppOpsNextApplication
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.guard.GuardEvent
import dev.izumi.appopsnext.guard.GuardEventKind
import dev.izumi.appopsnext.guard.GuardMode
import dev.izumi.appopsnext.guard.GuardNotifier
import dev.izumi.appopsnext.guard.GuardRestorePolicy
import dev.izumi.appopsnext.guard.GuardRule
import dev.izumi.appopsnext.appops.model.AppOpNames
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.presentation.app_detail.AppOpDisplayCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GuardViewModel(application: Application) : AndroidViewModel(application) {
    private val app = getApplication<AppOpsNextApplication>()
    private val repository = app.guardRepository
    val mode = repository.mode.stateIn(viewModelScope, SharingStarted.Eagerly, GuardMode.OFF)
    val rules = repository.rules.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val events = repository.events.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val restoreLimit = repository.restoreLimit
        .stateIn(viewModelScope, SharingStarted.Eagerly, GuardRestorePolicy.DEFAULT_LIMIT)
    private val mutableChecking = MutableStateFlow(false)
    val checking = mutableChecking.asStateFlow()

    fun setMode(mode: GuardMode) {
        viewModelScope.launch { repository.setMode(mode) }
    }

    /** Checks at once, so a restore already waiting follows the new timing rather than the old one. */
    fun saveRestore(rule: GuardRule, autoRestore: Boolean, minutes: Int, afterLeaving: Boolean) {
        viewModelScope.launch {
            repository.upsert(
                rule.copy(autoRestore = autoRestore, restoreDelayMinutes = minutes, restoreAfterLeaving = afterLeaving),
            )
            app.guardController.runCheckIfEnabled("timing_changed")
        }
    }

    /** Checks at once, so a rule held at the old limit is restored when the new one allows it. */
    fun setRestoreLimit(limit: Int) {
        viewModelScope.launch {
            repository.setRestoreLimit(limit)
            app.guardController.runCheckIfEnabled("limit_changed")
        }
    }

    fun remove(rule: GuardRule) {
        viewModelScope.launch { repository.remove(rule.id) }
    }

    fun checkNow() {
        if (mutableChecking.value) return
        mutableChecking.value = true
        viewModelScope.launch {
            try {
                app.guardController.runCheck("manual")
            } finally {
                mutableChecking.value = false
            }
        }
    }

    fun clearEvents() {
        viewModelScope.launch { repository.clearEvents() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardScreen(
    viewModel: GuardViewModel,
    onBack: () -> Unit,
    onChooseApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val notifier = (context.applicationContext as AppOpsNextApplication).guardNotifier
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()
    val restoreLimit by viewModel.restoreLimit.collectAsStateWithLifecycle()
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var showLimit by rememberSaveable { mutableStateOf(false) }
    var openRule by remember { mutableStateOf<Pair<GuardRule, String>?>(null) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.guard_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ph_arrow_left),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDetails = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ph_info),
                            contentDescription = stringResource(R.string.guard_details_title),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentPadding = PageListBottomPadding,
        ) {
            item {
                Text(
                    text = stringResource(R.string.guard_caption),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // What is guarded, or how to guard something, comes before how it is checked.
            item {
                Row(
                    modifier = Modifier.padding(start = 24.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.guard_rules_section),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (rules.isNotEmpty()) {
                        OutlinedButton(onClick = viewModel::checkNow, enabled = !checking) {
                            Text(stringResource(if (checking) R.string.guard_checking else R.string.guard_check_now))
                        }
                    }
                }
            }
            if (rules.isEmpty()) {
                item { GuardEmptyCard(modeOff = mode == GuardMode.OFF, onChooseApp = onChooseApp) }
            } else {
                item {
                    ExperimentalGroup {
                        rules.forEachIndexed { index, rule ->
                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            GuardRuleRow(rule = rule, onOpen = { title -> openRule = rule to title })
                        }
                    }
                }
            }
            item {
                ExperimentalSectionTitle(stringResource(R.string.guard_mode_section))
                ExperimentalGroup {
                    GuardMode.entries.forEachIndexed { index, option ->
                        if (index > 0) HorizontalDivider(modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                        ExperimentalGroupRow(
                            modifier = Modifier.selectable(
                                selected = mode == option,
                                role = Role.RadioButton,
                                onClick = { viewModel.setMode(option) },
                            ),
                            leadingContent = { RadioButton(selected = mode == option, onClick = null) },
                            headlineContent = { Text(stringResource(modeTitle(option))) },
                            supportingContent = { Text(stringResource(modeSummary(option))) },
                        )
                    }
                }
            }
            item {
                ExperimentalSectionTitle(stringResource(R.string.guard_restore_section))
                ExperimentalGroup {
                    ExperimentalGroupRow(
                        modifier = Modifier.clickable { showLimit = true },
                        headlineContent = { Text(stringResource(R.string.guard_restore_limit_title)) },
                        supportingContent = {
                            Text(pluralStringResource(R.plurals.guard_restore_limit_summary, restoreLimit, restoreLimit))
                        },
                        trailingContent = { MainPageChevron() },
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.padding(start = 24.dp, top = 20.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.guard_events_section),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (events.isNotEmpty()) {
                        TextButton(onClick = viewModel::clearEvents) { Text(stringResource(R.string.guard_events_clear)) }
                    }
                }
            }
            if (events.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.guard_events_empty),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item {
                    ExperimentalGroup(modifier = Modifier.padding(top = 8.dp)) {
                        events.take(MAX_SHOWN_EVENTS).forEachIndexed { index, event ->
                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            GuardEventRow(event = event, notifier = notifier)
                        }
                    }
                }
            }
        }
    }

    if (showDetails) {
        AppBottomSheet(
            onDismissRequest = { showDetails = false },
            title = { Text(stringResource(R.string.guard_details_title)) },
            text = { Text(stringResource(R.string.guard_details)) },
            confirmButton = {
                Button(onClick = { showDetails = false }) { Text(stringResource(R.string.action_dismiss)) }
            },
        )
    }
    if (showLimit) {
        NumberSheet(
            title = stringResource(R.string.guard_restore_limit_title),
            message = stringResource(R.string.guard_restore_limit_message),
            initial = restoreLimit,
            default = GuardRestorePolicy.DEFAULT_LIMIT,
            range = GuardRestorePolicy.LIMIT_RANGE,
            label = stringResource(R.string.guard_restore_limit_label),
            placeholder = stringResource(
                R.string.guard_restore_limit_placeholder,
                GuardRestorePolicy.DEFAULT_LIMIT,
                GuardRestorePolicy.LIMIT_RANGE.last,
            ),
            onDismiss = { showLimit = false },
            onSave = { limit ->
                showLimit = false
                viewModel.setRestoreLimit(limit)
            },
        )
    }
    openRule?.let { (opened, title) ->
        // The rule as stored now, so a switch flipped meanwhile is not undone by a save.
        val rule = rules.firstOrNull { it.id == opened.id } ?: opened
        var autoRestore by rememberSaveable(opened.id) { mutableStateOf(rule.autoRestore) }
        var afterLeaving by rememberSaveable(opened.id) { mutableStateOf(rule.restoreAfterLeaving) }
        NumberSheet(
            title = title,
            message = stringResource(R.string.guard_rule_delay_message),
            initial = rule.restoreDelayMinutes,
            default = 0,
            range = GuardRestorePolicy.DELAY_RANGE,
            label = stringResource(R.string.guard_rule_delay_label),
            placeholder = stringResource(R.string.guard_rule_delay_placeholder, GuardRestorePolicy.DELAY_RANGE.last),
            onDismiss = { openRule = null },
            onSave = { minutes ->
                openRule = null
                if (autoRestore != rule.autoRestore || minutes != rule.restoreDelayMinutes ||
                    afterLeaving != rule.restoreAfterLeaving
                ) {
                    viewModel.saveRestore(rule, autoRestore, minutes, afterLeaving)
                }
            },
            // When to restore matters only while restoring is on, so it shows only then.
            fieldVisible = autoRestore,
            leadingContent = {
                SheetSwitchRow(
                    title = stringResource(R.string.guard_rule_auto_restore),
                    summary = stringResource(R.string.guard_rule_auto_restore_summary),
                    checked = autoRestore,
                    onCheckedChange = { autoRestore = it },
                )
            },
            extraContent = {
                SheetSwitchRow(
                    title = stringResource(R.string.guard_rule_leave_title),
                    summary = stringResource(R.string.guard_rule_leave_summary),
                    checked = afterLeaving,
                    onCheckedChange = { afterLeaving = it },
                )
            },
            removeLabel = stringResource(R.string.guard_rule_remove),
            onRemove = {
                openRule = null
                viewModel.remove(rule)
            },
        )
    }
}

/**
 * A whole number in [range]. An empty field takes the default, which the
 * placeholder names. A value outside the range disables saving and says why.
 * A rule's sheet also offers to stop guarding it, as the leading action.
 */
@Composable
private fun NumberSheet(
    title: String,
    message: String,
    initial: Int,
    default: Int,
    range: IntRange,
    label: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
    leadingContent: (@Composable () -> Unit)? = null,
    extraContent: (@Composable () -> Unit)? = null,
    fieldVisible: Boolean = true,
    removeLabel: String? = null,
    onRemove: (() -> Unit)? = null,
) {
    var text by rememberSaveable { mutableStateOf(initial.toString()) }
    val value = text.toIntOrNull()
    val valid = !fieldVisible || text.isEmpty() || (value != null && value in range)
    AppBottomSheet(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                leadingContent?.invoke()
                if (fieldVisible) {
                    Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    extraContent?.invoke()
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        CompactTextField(
                            value = text,
                            onValueChange = { text = intervalFieldDigits(it) },
                            label = placeholder,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = !valid,
                        )
                        if (!valid) {
                            Text(
                                text = stringResource(R.string.guard_number_out_of_range, range.first, range.last),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        },
        leadingButton = if (onRemove != null && removeLabel != null) {
            {
                TextButton(onClick = onRemove) {
                    Text(removeLabel, color = MaterialTheme.colorScheme.error)
                }
            }
        } else {
            null
        },
        confirmButton = {
            Button(onClick = { onSave(if (fieldVisible) value ?: default else initial) }, enabled = valid) {
                Text(stringResource(R.string.guard_save))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun SheetSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CompactSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** No rule yet: what adding one takes, and the way to the app list where it is done. */
@Composable
private fun GuardEmptyCard(modeOff: Boolean, onChooseApp: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_ph_shield),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.guard_rules_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                // The option to guard a setting is offered only while a check mode is chosen.
                text = stringResource(if (modeOff) R.string.guard_rules_empty_mode_off else R.string.guard_rules_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onChooseApp,
                enabled = !modeOff,
                modifier = Modifier.padding(top = 4.dp),
            ) { Text(stringResource(R.string.guard_choose_app)) }
        }
    }
}

/**
 * The outcome comes first with the mode it was meant to be, so rows that share an
 * app and a permission still read apart. What was found goes underneath.
 */
@Composable
internal fun GuardEventRow(event: GuardEvent, notifier: GuardNotifier) {
    val title = remember(event.packageName, event.stableOperationName) {
        "${notifier.appLabel(event.packageName)} · ${notifier.operationLabel(event.stableOperationName)}"
    }
    val (iconRes, tint) = when (event.kind) {
        GuardEventKind.RESTORED -> R.drawable.ic_ph_check_circle to MaterialTheme.colorScheme.primary
        GuardEventKind.DRIFT -> R.drawable.ic_ph_warning_circle to MaterialTheme.colorScheme.tertiary
        GuardEventKind.RESTORE_FAILED -> R.drawable.ic_ph_x to MaterialTheme.colorScheme.error
        GuardEventKind.RESTORE_LIMIT -> R.drawable.ic_ph_warning_circle to MaterialTheme.colorScheme.error
        GuardEventKind.RESTORE_PENDING -> R.drawable.ic_ph_clock_counter_clockwise to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val expected = stringResource(GuardNotifier.modeLabelRes(event.expectedMode))
    val headline = when (event.kind) {
        GuardEventKind.RESTORED -> stringResource(R.string.guard_event_headline_restored, expected)
        GuardEventKind.DRIFT -> stringResource(R.string.guard_event_headline_drift, expected)
        GuardEventKind.RESTORE_FAILED -> stringResource(R.string.guard_event_headline_failed, expected)
        GuardEventKind.RESTORE_LIMIT -> stringResource(R.string.guard_event_headline_limit)
        GuardEventKind.RESTORE_PENDING -> stringResource(R.string.guard_event_headline_pending, expected)
    }
    val needsAttention = event.kind == GuardEventKind.RESTORE_FAILED || event.kind == GuardEventKind.RESTORE_LIMIT
    val detected = stringResource(
        R.string.guard_event_detected,
        stringResource(GuardNotifier.modeLabelRes(event.observedMode)),
    )
    ExperimentalGroupRow(
        leadingContent = { Icon(painter = painterResource(iconRes), contentDescription = null, tint = tint) },
        headlineContent = {
            Text(
                text = headline,
                color = if (needsAttention) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        },
        supportingContent = { Text("$title\n$detected") },
        trailingContent = {
            Text(
                text = DateUtils.getRelativeTimeSpanString(
                    event.timeMillis,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE,
                ).toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun GuardRuleRow(
    rule: GuardRule,
    onOpen: (title: String) -> Unit,
) {
    val context = LocalContext.current
    val appLabel = remember(rule.packageName) {
        runCatching {
            val info = context.packageManager.getApplicationInfo(
                rule.packageName,
                PackageManager.ApplicationInfoFlags.of(0),
            )
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(rule.packageName)
    }
    val shellName = AppOpNames.shellName(rule.stableOperationName)
    val operationLabel = AppOpDisplayCatalog.labelResOf(shellName)?.let { stringResource(it) } ?: shellName
    val title = "$appLabel · $operationLabel"
    val expected = stringResource(
        if (rule.scope == AppOpScope.UID) R.string.guard_rule_expected_uid else R.string.guard_rule_expected,
        stringResource(GuardNotifier.modeLabelRes(rule.expectedMode)),
    )
    val delay = rule.restoreDelayMinutes
    val restore = when {
        !rule.autoRestore -> stringResource(R.string.guard_rule_notify_only)
        rule.restoreAfterLeaving && delay > 0 -> stringResource(R.string.guard_rule_delay_leave_timing, delay)
        rule.restoreAfterLeaving -> stringResource(R.string.guard_rule_leave_timing)
        delay > 0 -> stringResource(R.string.guard_rule_delay_summary, delay)
        else -> stringResource(R.string.guard_rule_restore_at_once)
    }
    // Everything about the rule sits behind a tap on the row, so the row keeps its width for text.
    ExperimentalGroupRow(
        modifier = Modifier.clickable { onOpen(title) },
        headlineContent = { Text(title) },
        supportingContent = { Text("$expected\n$restore") },
        trailingContent = { MainPageChevron() },
    )
}

internal fun modeTitle(mode: GuardMode): Int = when (mode) {
    GuardMode.OFF -> R.string.guard_mode_off
    GuardMode.PERIODIC -> R.string.guard_mode_periodic
    GuardMode.REALTIME -> R.string.guard_mode_realtime
}

private fun modeSummary(mode: GuardMode): Int = when (mode) {
    GuardMode.OFF -> R.string.guard_mode_off_summary
    GuardMode.PERIODIC -> R.string.guard_mode_periodic_summary
    GuardMode.REALTIME -> R.string.guard_mode_realtime_summary
}

private const val MAX_SHOWN_EVENTS = 50
