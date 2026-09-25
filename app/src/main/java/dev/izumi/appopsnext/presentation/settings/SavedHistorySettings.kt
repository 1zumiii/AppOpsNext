package dev.izumi.appopsnext.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.presentation.components.AppBottomSheet
import dev.izumi.appopsnext.history.HistoryArchiveProblem
import dev.izumi.appopsnext.history.HistoryArchiveRecorder
import dev.izumi.appopsnext.presentation.app_detail.AppOpDisplayCatalog
import dev.izumi.appopsnext.presentation.components.MainPageChevron
import dev.izumi.appopsnext.presentation.components.MainPageEntryIcon
import java.text.DateFormat
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/** The settings row that leads to [SavedHistoryScreen]. */
@Composable
fun SavedHistoryEntry(uiState: SettingsUiState, onOpen: () -> Unit) {
    val count = uiState.savedHistory.recordCount
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        leadingContent = { MainPageEntryIcon(R.drawable.ic_ph_archive) },
        headlineContent = { Text(text = stringResource(R.string.settings_saved_history_entry)) },
        supportingContent = {
            Text(
                text = when {
                    uiState.savedHistory.problem == HistoryArchiveProblem.UNREADABLE ->
                        stringResource(R.string.settings_saved_history_status_unreadable)
                    uiState.savedHistory.problem == HistoryArchiveProblem.WRITE_FAILED ->
                        stringResource(R.string.settings_saved_history_status_write_failed)
                    uiState.saveIndividualHistory -> stringResource(R.string.settings_saved_history_status_on, count)
                    count > 0 -> stringResource(R.string.settings_saved_history_status_off_kept, count)
                    else -> stringResource(R.string.settings_saved_history_status_off)
                },
            )
        },
        trailingContent = { MainPageChevron() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedHistoryScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onSaveIndividualHistoryChange: (Boolean) -> Unit,
    onSavedHistoryOperationChange: (String, Boolean) -> Unit,
    countSavedHistory: (SavedHistoryDateRange?) -> Int,
    onDeleteSavedHistory: (SavedHistoryDateRange?) -> Unit,
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val saved = uiState.savedHistory
    val earliest = saved.oldestMillis?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }
    val latest = LocalDate.now(zoneId)
    var startDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var endDay by rememberSaveable { mutableStateOf<Long?>(null) }
    // A chosen range stays valid when records are deleted or saved underneath it.
    val range = earliest?.let {
        SavedHistoryDateRange(
            start = startDay?.let(LocalDate::ofEpochDay) ?: it,
            end = endDay?.let(LocalDate::ofEpochDay) ?: latest,
        ).clampedTo(it, latest)
    }
    var picking by remember { mutableStateOf<DateEnd?>(null) }
    var showHelp by remember { mutableStateOf(false) }
    var operationsExpanded by rememberSaveable { mutableStateOf(false) }
    var confirming by remember { mutableStateOf<Deletion?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_saved_history_entry)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showHelp = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_action_info),
                            contentDescription = stringResource(R.string.settings_saved_history_help_title),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                ListItem(
                    modifier = Modifier.clickable {
                        onSaveIndividualHistoryChange(!uiState.saveIndividualHistory)
                    },
                    headlineContent = { Text(text = stringResource(R.string.settings_save_history)) },
                    supportingContent = { Text(text = stringResource(R.string.settings_save_history_detail)) },
                    trailingContent = {
                        Switch(
                            checked = uiState.saveIndividualHistory,
                            onCheckedChange = onSaveIndividualHistoryChange,
                        )
                    },
                )
            }
            item {
                // Collapsed by default: the four choices are set once and rarely revisited.
                ListItem(
                    modifier = Modifier.clickable { operationsExpanded = !operationsExpanded },
                    headlineContent = { Text(text = stringResource(R.string.settings_saved_history_operations)) },
                    supportingContent = { Text(text = savedOperationsSummary(uiState.savedHistoryOperations)) },
                    trailingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_right),
                            contentDescription = stringResource(
                                if (operationsExpanded) R.string.watchers_collapse else R.string.watchers_expand,
                            ),
                            modifier = Modifier.rotate(if (operationsExpanded) 90f else 0f),
                        )
                    },
                )
            }
            if (operationsExpanded) {
                item {
                    Text(
                        text = stringResource(R.string.settings_saved_history_operations_hint),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(HistoryArchiveRecorder.SAVEABLE_OPERATIONS, key = { it }) { operation ->
                    val saved = operation in uiState.savedHistoryOperations
                    ListItem(
                        modifier = Modifier
                            .clickable { onSavedHistoryOperationChange(operation, !saved) }
                            .padding(start = 16.dp),
                        headlineContent = { Text(text = operationLabel(operation)) },
                        trailingContent = {
                            Checkbox(
                                checked = saved,
                                onCheckedChange = { onSavedHistoryOperationChange(operation, it) },
                            )
                        },
                    )
                }
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item {
                Text(
                    text = stringResource(R.string.settings_saved_history),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = savedHistoryText(saved),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                SavedHistoryUsage(
                    summary = saved,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            saved.problem?.let { problem ->
                item {
                    Text(
                        text = stringResource(
                            when (problem) {
                                HistoryArchiveProblem.UNREADABLE -> R.string.settings_saved_history_unreadable
                                HistoryArchiveProblem.WRITE_FAILED -> R.string.settings_saved_history_write_failed
                            },
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    // The range controls need readable records, so this is the one way out.
                    if (problem == HistoryArchiveProblem.UNREADABLE) {
                        TextButton(
                            onClick = { confirming = Deletion(null, count = 0, unreadable = true) },
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) { Text(text = stringResource(R.string.settings_delete_all_saved)) }
                    }
                }
            }
            if (range != null && earliest != null) {
                item {
                    ListItem(
                        modifier = Modifier.clickable { picking = DateEnd.START },
                        headlineContent = { Text(text = stringResource(R.string.settings_delete_range_start)) },
                        supportingContent = { Text(text = formatDate(range.start, zoneId)) },
                    )
                    ListItem(
                        modifier = Modifier.clickable { picking = DateEnd.END },
                        headlineContent = { Text(text = stringResource(R.string.settings_delete_range_end)) },
                        supportingContent = { Text(text = formatDate(range.end, zoneId)) },
                    )
                    Text(
                        text = stringResource(R.string.settings_delete_range_hint),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = { confirming = Deletion(range, countSavedHistory(range)) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) { Text(text = stringResource(R.string.settings_delete_range)) }
                        TextButton(
                            onClick = { confirming = Deletion(null, countSavedHistory(null)) },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) { Text(text = stringResource(R.string.settings_delete_all_saved)) }
                    }
                }
            }
        }
    }

    if (showHelp) {
        AppBottomSheet(
            onDismissRequest = { showHelp = false },
            title = { Text(text = stringResource(R.string.settings_saved_history_help_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    listOf(
                        R.string.settings_saved_history_help_why,
                        R.string.settings_saved_history_help_what,
                        R.string.settings_saved_history_help_gaps,
                        R.string.settings_saved_history_help_storage,
                        R.string.settings_saved_history_help_off,
                        R.string.settings_saved_history_help_unreadable,
                    ).forEach { Text(text = stringResource(it)) }
                }
            },
            confirmButton = {
                Button(onClick = { showHelp = false }) {
                    Text(text = stringResource(R.string.action_dismiss))
                }
            },
        )
    }
    val end = picking
    if (end != null && range != null && earliest != null) {
        SavedHistoryDatePicker(
            initial = if (end == DateEnd.START) range.start else range.end,
            earliest = earliest,
            latest = latest,
            onConfirm = { date ->
                val updated = if (end == DateEnd.START) {
                    range.withStart(date, earliest, latest)
                } else {
                    range.withEnd(date, earliest, latest)
                }
                startDay = updated.start.toEpochDay()
                endDay = updated.end.toEpochDay()
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
    confirming?.let { deletion ->
        AppBottomSheet(
            onDismissRequest = { confirming = null },
            title = { Text(text = stringResource(R.string.settings_delete_saved_history_title)) },
            text = {
                Text(
                    text = when {
                        deletion.unreadable -> stringResource(R.string.settings_delete_confirm_unreadable)
                        deletion.count == 0 -> stringResource(R.string.settings_delete_nothing)
                        deletion.range == null -> stringResource(R.string.settings_delete_confirm_all, deletion.count)
                        else -> stringResource(
                            R.string.settings_delete_confirm_range,
                            formatDate(deletion.range.start, zoneId),
                            formatDate(deletion.range.end, zoneId),
                            deletion.count,
                        )
                    },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirming = null
                        onDeleteSavedHistory(deletion.range)
                    },
                    enabled = deletion.unreadable || deletion.count > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text(text = stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirming = null }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * How full the archive is, turning amber and then red as it nears the point
 * where the oldest records start being removed.
 */
@Composable
private fun SavedHistoryUsage(summary: SavedHistorySummary, modifier: Modifier = Modifier) {
    val fraction = (summary.recordCount.toFloat() / summary.capacity).coerceIn(0f, 1f)
    val filling = fraction >= FILLING_FRACTION
    val color = when {
        fraction >= NEARLY_FULL_FRACTION -> MaterialTheme.colorScheme.error
        filling -> if (isSystemInDarkTheme()) AMBER_ON_DARK else AMBER_ON_LIGHT
        else -> MaterialTheme.colorScheme.primary
    }
    val numbers = remember { NumberFormat.getIntegerInstance() }
    val percent = remember { NumberFormat.getPercentInstance().apply { maximumFractionDigits = 1 } }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Text(
            text = stringResource(
                R.string.settings_saved_history_usage,
                numbers.format(summary.recordCount),
                numbers.format(summary.capacity),
                percent.format(fraction),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (filling) color else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (filling) {
            Text(
                text = stringResource(R.string.settings_saved_history_nearly_full),
                style = MaterialTheme.typography.bodySmall,
                color = color,
            )
        }
    }
}

private const val FILLING_FRACTION = 0.75f
private const val NEARLY_FULL_FRACTION = 0.9f
private val AMBER_ON_LIGHT = Color(0xFFB26A00)
private val AMBER_ON_DARK = Color(0xFFFFB74D)

private enum class DateEnd { START, END }

/**
 * A null range means everything; the count is what the dialog promises to remove.
 * An unreadable file has no count to promise, only the file itself.
 */
private data class Deletion(val range: SavedHistoryDateRange?, val count: Int, val unreadable: Boolean = false)

/**
 * The picker only offers days inside the saved span; the range clamps again on
 * confirm, so no input can produce a reversed or out-of-span range.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedHistoryDatePicker(
    initial: LocalDate,
    earliest: LocalDate,
    latest: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // The picker speaks UTC midnights; a local date maps to the same calendar day.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toEpochDay() * MILLIS_PER_DAY,
        yearRange = earliest.year..latest.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                Math.floorDiv(utcTimeMillis, MILLIS_PER_DAY) in earliest.toEpochDay()..latest.toEpochDay()

            override fun isSelectableYear(year: Int): Boolean = year in earliest.year..latest.year
        },
    )
    AppBottomSheet(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    state.selectedDateMillis?.let { onConfirm(LocalDate.ofEpochDay(Math.floorDiv(it, MILLIS_PER_DAY))) }
                },
                enabled = state.selectedDateMillis != null,
            ) { Text(text = stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(text = stringResource(R.string.action_cancel)) }
        },
        bodyHorizontalPadding = 0.dp,
        text = {
            DatePicker(
                state = state,
                modifier = Modifier.fillMaxWidth(),
                colors = DatePickerDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    )
}

@Composable
private fun savedHistoryText(summary: SavedHistorySummary): String {
    val oldest = summary.oldestMillis ?: return stringResource(R.string.settings_saved_history_empty)
    val newest = summary.newestMillis ?: oldest
    val format = DateFormat.getDateInstance(DateFormat.MEDIUM)
    return stringResource(
        R.string.settings_saved_history_summary,
        summary.recordCount,
        format.format(Date(oldest)),
        format.format(Date(newest)),
    )
}

@Composable
private fun operationLabel(operation: String): String =
    AppOpDisplayCatalog.labelResOf(operation)?.let { stringResource(it) } ?: operation

/** The chosen operations in the order they are offered, or that none are. */
@Composable
private fun savedOperationsSummary(saved: Set<String>): String {
    val labels = HistoryArchiveRecorder.SAVEABLE_OPERATIONS.filter { it in saved }.map { operationLabel(it) }
    return if (labels.isEmpty()) {
        stringResource(R.string.settings_saved_history_operations_none)
    } else {
        labels.joinToString(stringResource(R.string.list_separator))
    }
}

private fun formatDate(date: LocalDate, zoneId: ZoneId): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(date.atStartOfDay(zoneId).toInstant().toEpochMilli()))

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
