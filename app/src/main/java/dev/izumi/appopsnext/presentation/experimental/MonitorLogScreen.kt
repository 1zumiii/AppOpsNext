package dev.izumi.appopsnext.presentation.experimental

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.presentation.components.AppBottomSheet
import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.monitor.MonitorLogEntry
import dev.izumi.appopsnext.monitor.MonitorOutcomes
import dev.izumi.appopsnext.presentation.app_detail.AppOpDisplayCatalog
import dev.izumi.appopsnext.presentation.components.AppIcon
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Every access the monitoring points were told about, newest first, grouped by
 * day. Tapping one opens that application's permissions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorLogScreen(
    entries: List<MonitorLogEntry>,
    apps: List<InstalledApp>,
    onBack: () -> Unit,
    onOpenApp: (InstalledApp) -> Unit,
    onClear: () -> Unit,
) {
    var outcome by rememberSaveable { mutableStateOf(MonitorOutcomes.ALL) }
    var confirmingClear by remember { mutableStateOf(false) }
    val appsByPackage = remember(apps) { apps.associateBy(InstalledApp::packageName) }
    val zoneId = remember { ZoneId.systemDefault() }
    // Up to fifty thousand entries are sorted into days, which is not work for the main thread.
    val rows by produceState<List<LogRow>?>(null, entries, outcome, zoneId) {
        value = withContext(Dispatchers.Default) {
            logRows(entries.asReversed().filter { outcome.reports(it.allowed) }, zoneId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.monitor_log_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { confirmingClear = true }, enabled = entries.isNotEmpty()) {
                        Icon(
                            painter = painterResource(R.drawable.ic_action_delete),
                            contentDescription = stringResource(R.string.monitor_log_clear),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    text = stringResource(R.string.monitor_log_hint),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MonitorOutcomes.entries.forEach { option ->
                        FilterChip(
                            selected = option == outcome,
                            onClick = { outcome = option },
                            label = { Text(text = stringResource(option.logLabelRes())) },
                        )
                    }
                }
            }
            val shown = rows ?: return@LazyColumn
            if (shown.isEmpty()) {
                item {
                    Text(
                        text = stringResource(
                            if (entries.isEmpty()) R.string.monitor_log_empty else R.string.monitor_log_empty_filtered,
                        ),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(shown, key = LogRow::key) { row ->
                when (row) {
                    is LogRow.Day -> DayHeader(row.date)
                    is LogRow.Access -> {
                        // Only the app that holds this UID now; a reinstalled one is another app.
                        val app = appsByPackage[row.entry.packageName]?.takeIf { it.uid == row.entry.uid }
                        AccessRow(row.entry, app, zoneId, onOpenApp)
                    }
                }
            }
        }
    }

    if (confirmingClear) {
        AppBottomSheet(
            onDismissRequest = { confirmingClear = false },
            title = { Text(text = stringResource(R.string.monitor_log_clear)) },
            text = { Text(text = stringResource(R.string.monitor_log_clear_confirm, entries.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingClear = false
                        onClear()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(text = stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClear = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun DayHeader(date: LocalDate) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val formatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    Text(
        text = date.format(formatter),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun AccessRow(
    entry: MonitorLogEntry,
    app: InstalledApp?,
    zoneId: ZoneId,
    onOpenApp: (InstalledApp) -> Unit,
) {
    val label = app?.label ?: entry.packageName
    val operation = AppOpDisplayCatalog.labelResOf(entry.operationName)?.let { stringResource(it) }
        ?: entry.operationName
    val time = remember(entry.timeMillis, zoneId) {
        Instant.ofEpochMilli(entry.timeMillis).atZone(zoneId).toLocalTime().format(TIME_FORMAT)
    }
    ListItem(
        modifier = if (app != null) Modifier.clickable { onOpenApp(app) } else Modifier,
        leadingContent = { AppIcon(packageName = entry.packageName, appLabel = label) },
        headlineContent = { Text(text = label) },
        supportingContent = {
            Text(
                text = stringResource(
                    if (entry.allowed) R.string.monitor_access_allowed else R.string.monitor_access_refused,
                    operation,
                ),
                color = if (entry.allowed) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        },
        trailingContent = {
            Text(
                text = time,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

private sealed interface LogRow {
    val key: String

    /** [index] keeps a day apart from itself when the clock was set back across midnight. */
    data class Day(val date: LocalDate, val index: Int) : LogRow {
        override val key: String get() = "day:$index:$date"
    }

    /** [index] keeps two identical accesses in one millisecond apart as list keys. */
    data class Access(val entry: MonitorLogEntry, val index: Int) : LogRow {
        override val key: String get() = "access:$index:${entry.timeMillis}"
    }
}

/** Newest-first entries with a header wherever the local day changes. */
private fun logRows(newestFirst: List<MonitorLogEntry>, zoneId: ZoneId): List<LogRow> {
    val rows = ArrayList<LogRow>(newestFirst.size + 32)
    var day: LocalDate? = null
    newestFirst.forEachIndexed { index, entry ->
        val date = Instant.ofEpochMilli(entry.timeMillis).atZone(zoneId).toLocalDate()
        if (date != day) {
            rows += LogRow.Day(date, index)
            day = date
        }
        rows += LogRow.Access(entry, index)
    }
    return rows
}

private fun MonitorOutcomes.logLabelRes(): Int = when (this) {
    MonitorOutcomes.ALL -> R.string.monitor_outcome_all
    MonitorOutcomes.REFUSED -> R.string.monitor_outcome_refused
    MonitorOutcomes.ALLOWED -> R.string.monitor_outcome_allowed
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
