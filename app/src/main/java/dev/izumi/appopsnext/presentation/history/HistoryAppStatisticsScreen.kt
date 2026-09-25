package dev.izumi.appopsnext.presentation.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.history.model.HistoryPermission
import dev.izumi.appopsnext.presentation.components.AppIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryAppStatisticsScreen(
    permission: HistoryPermission,
    timeRange: HistoryTimeRange,
    history: PermissionHistory?,
    onBack: () -> Unit,
    onAppSelected: (InstalledApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val summaries = remember(history?.events, history?.individualRecordsAvailable) {
        HistoryAppStatistics.summarize(
            history?.events.orEmpty(),
            history?.individualRecordsAvailable == true,
        )
    }
    var sortOrder by rememberSaveable(permission.shellOperationName) {
        mutableStateOf(HistoryAppSort.TOTAL_ACTIVITY)
    }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val sortedSummaries = remember(summaries, sortOrder) {
        HistoryAppStatistics.sort(summaries, sortOrder)
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            R.string.history_apps_involved,
                        ),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(
                                R.drawable.ic_ph_arrow_left,
                            ),
                            contentDescription = stringResource(
                                R.string.action_back,
                            ),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = HistoryContentPadding,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            R.string.history_apps_context,
                            permission.displayName(),
                            stringResource(timeRange.contextLabelRes()),
                        ),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box {
                        OutlinedButton(onClick = { sortMenuExpanded = true }) {
                            Text(text = stringResource(sortOrder.labelRes()))
                            Icon(
                                painter = painterResource(R.drawable.ic_ph_caret_right),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp).rotate(90f),
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false },
                        ) {
                            HistoryAppSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(option.labelRes())) },
                                    onClick = {
                                        sortOrder = option
                                        sortMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.history_apps_count, summaries.size),
                    modifier = Modifier.padding(bottom = 14.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (summaries.isEmpty()) {
                item {
                    Text(
                        text = stringResource(
                            R.string.history_apps_empty,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                itemsIndexed(
                    items = sortedSummaries,
                    key = { _, summary -> summary.app.packageName },
                ) { index, summary ->
                    HistoryAppStatisticsItem(
                        summary = summary,
                        onClick = { onAppSelected(summary.app) },
                        isFirst = index == 0,
                        isLast = index == sortedSummaries.lastIndex,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryAppStatisticsItem(
    summary: AppHistorySummary,
    onClick: () -> Unit,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
) {
    val accesses = stringResource(R.string.history_apps_access_unit)
    val denied = stringResource(R.string.history_apps_denied_unit)
    val metricText = buildAnnotatedString {
        append(summary.accessCount.toString())
        append(" ")
        append(accesses)
        append(" · ")
        if (summary.rejectCount > 0) {
            pushStyle(SpanStyle(color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold))
        }
        append(summary.rejectCount.toString())
        if (summary.rejectCount > 0) pop()
        append(" ")
        append(denied)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(
            topStart = if (isFirst) 18.dp else 0.dp,
            topEnd = if (isFirst) 18.dp else 0.dp,
            bottomStart = if (isLast) 18.dp else 0.dp,
            bottomEnd = if (isLast) 18.dp else 0.dp,
        ),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(
                    packageName = summary.app.packageName,
                    appLabel = summary.app.label,
                    size = 40.dp,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = summary.app.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = summary.app.packageName,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = metricText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    if (summary.intervalAccessCount > 0) {
                        Text(
                            text = stringResource(
                                R.string.history_apps_interval_count,
                                summary.intervalAccessCount,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (!isLast) HorizontalDivider(modifier = Modifier.padding(start = 68.dp, end = 16.dp))
        }
    }
}

private fun HistoryTimeRange.contextLabelRes(): Int = when (this) {
    HistoryTimeRange.TODAY -> R.string.history_range_today
    HistoryTimeRange.WEEK -> R.string.history_range_week
    HistoryTimeRange.MONTH -> R.string.history_range_month
    HistoryTimeRange.ALL -> R.string.history_range_all
}

private fun HistoryAppSort.labelRes(): Int = when (this) {
    HistoryAppSort.TOTAL_ACTIVITY -> R.string.history_apps_sort_activity
    HistoryAppSort.ACCESSES -> R.string.history_apps_sort_accesses
    HistoryAppSort.DENIALS -> R.string.history_apps_sort_denials
    HistoryAppSort.RECENT -> R.string.history_apps_sort_recent
}
