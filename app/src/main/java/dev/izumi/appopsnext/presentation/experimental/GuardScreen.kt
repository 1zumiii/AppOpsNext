package dev.izumi.appopsnext.presentation.experimental

import android.app.Application
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import android.content.pm.PackageManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
    private val mutableChecking = MutableStateFlow(false)
    val checking = mutableChecking.asStateFlow()

    fun setMode(mode: GuardMode) {
        viewModelScope.launch { repository.setMode(mode) }
    }

    fun setAutoRestore(rule: GuardRule, enabled: Boolean) {
        viewModelScope.launch { repository.upsert(rule.copy(autoRestore = enabled)) }
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val notifier = (context.applicationContext as AppOpsNextApplication).guardNotifier
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()
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
            )
        },
    ) { contentPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            item {
                Text(
                    text = stringResource(R.string.guard_caption),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                Row(
                    modifier = Modifier.padding(start = 24.dp, top = 20.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.guard_rules_section),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = viewModel::checkNow,
                        enabled = !checking && rules.isNotEmpty(),
                    ) {
                        Text(stringResource(if (checking) R.string.guard_checking else R.string.guard_check_now))
                    }
                }
            }
            if (rules.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.guard_rules_empty),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item {
                    ExperimentalGroup(modifier = Modifier.padding(top = 8.dp)) {
                        rules.forEachIndexed { index, rule ->
                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            GuardRuleRow(
                                rule = rule,
                                onAutoRestoreChange = { viewModel.setAutoRestore(rule, it) },
                                onRemove = { viewModel.remove(rule) },
                            )
                        }
                    }
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
}

@Composable
private fun GuardEventRow(event: GuardEvent, notifier: GuardNotifier) {
    val title = remember(event.packageName, event.stableOperationName) {
        "${notifier.appLabel(event.packageName)} · ${notifier.operationLabel(event.stableOperationName)}"
    }
    val (iconRes, tint) = when (event.kind) {
        GuardEventKind.RESTORED -> R.drawable.ic_ph_check_circle to MaterialTheme.colorScheme.primary
        GuardEventKind.DRIFT -> R.drawable.ic_ph_warning_circle to MaterialTheme.colorScheme.tertiary
        GuardEventKind.RESTORE_FAILED -> R.drawable.ic_ph_x to MaterialTheme.colorScheme.error
        GuardEventKind.RESTORE_LIMIT -> R.drawable.ic_ph_clock_counter_clockwise to MaterialTheme.colorScheme.error
    }
    val outcome = stringResource(
        when (event.kind) {
            GuardEventKind.RESTORED -> R.string.guard_event_outcome_restored
            GuardEventKind.DRIFT -> R.string.guard_event_outcome_drift
            GuardEventKind.RESTORE_FAILED -> R.string.guard_event_outcome_failed
            GuardEventKind.RESTORE_LIMIT -> R.string.guard_event_outcome_limit
        },
    )
    val change = stringResource(
        R.string.guard_event_change,
        stringResource(GuardNotifier.modeLabelRes(event.observedMode)),
    )
    ExperimentalGroupRow(
        leadingContent = { Icon(painter = painterResource(iconRes), contentDescription = null, tint = tint) },
        headlineContent = { Text(title) },
        supportingContent = { Text("$change · $outcome") },
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
    onAutoRestoreChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
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
    ExperimentalGroupRow(
        headlineContent = { Text("$appLabel · $operationLabel") },
        supportingContent = {
            val expected = stringResource(
                if (rule.scope == AppOpScope.UID) R.string.guard_rule_expected_uid else R.string.guard_rule_expected,
                stringResource(GuardNotifier.modeLabelRes(rule.expectedMode)),
            )
            val autoRestore = stringResource(R.string.guard_rule_auto_restore)
            Text(if (rule.autoRestore) "$expected · $autoRestore" else expected)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val autoRestoreLabel = stringResource(R.string.guard_rule_auto_restore)
                Switch(
                    checked = rule.autoRestore,
                    onCheckedChange = onAutoRestoreChange,
                    modifier = Modifier.semantics { contentDescription = autoRestoreLabel },
                )
                IconButton(onClick = onRemove) {
                    Icon(
                        painter = painterResource(R.drawable.ic_ph_trash),
                        contentDescription = stringResource(R.string.guard_rule_remove),
                    )
                }
            }
        },
    )
}

private fun modeTitle(mode: GuardMode): Int = when (mode) {
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
