package dev.izumi.appopsnext.presentation.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.presentation.components.AppBottomSheet
import dev.izumi.appopsnext.presentation.components.AppIcon

@Composable
fun HistoryFilterBar(
    range: HistoryTimeRange,
    ranges: List<HistoryTimeRange>,
    onRangeChange: (HistoryTimeRange) -> Unit,
    modifier: Modifier = Modifier,
    /** Null leaves the app filter out, as on the overview. */
    appFilterLabel: String? = null,
    appFilterActive: Boolean = false,
    onAppFilterClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ranges.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == range,
                    onClick = { onRangeChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, ranges.size),
                    label = { Text(text = stringResource(option.labelRes())) },
                )
            }
        }
        if (onAppFilterClick != null && appFilterLabel != null) {
            FilterChip(
                selected = appFilterActive,
                onClick = onAppFilterClick,
                label = {
                    Text(
                        text = appFilterLabel,
                        modifier = Modifier.widthIn(max = 160.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

/** Only apps with records in the range are listed, so the list needs no counts. */
@Composable
fun HistoryAppFilterDialog(
    summaries: List<AppHistorySummary>,
    selectedPackage: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AppBottomSheet(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.history_filter_app_title)) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                item {
                    Text(
                        text = stringResource(R.string.history_filter_app_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    ListItem(
                        modifier = Modifier.clickable { onSelect(null) },
                        headlineContent = {
                            Text(text = stringResource(R.string.history_filter_all_apps))
                        },
                        trailingContent = {
                            RadioButton(selected = selectedPackage == null, onClick = { onSelect(null) })
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
                items(items = summaries, key = { it.app.packageName }) { summary ->
                    val packageName = summary.app.packageName
                    ListItem(
                        modifier = Modifier.clickable { onSelect(packageName) },
                        leadingContent = {
                            AppIcon(
                                packageName = packageName,
                                appLabel = summary.app.label,
                                size = 32.dp,
                            )
                        },
                        headlineContent = {
                            Text(
                                text = summary.app.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            RadioButton(
                                selected = selectedPackage == packageName,
                                onClick = { onSelect(packageName) },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun HistoryTimeRange.labelRes(): Int = when (this) {
    HistoryTimeRange.TODAY -> R.string.history_range_today
    HistoryTimeRange.WEEK -> R.string.history_range_week
    HistoryTimeRange.MONTH -> R.string.history_range_month
    HistoryTimeRange.ALL -> R.string.history_range_all
}
