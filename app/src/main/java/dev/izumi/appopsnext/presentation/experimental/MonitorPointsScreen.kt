package dev.izumi.appopsnext.presentation.experimental

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.monitor.MonitorPointSettings
import dev.izumi.appopsnext.presentation.components.AppIcon

/** One monitoring point as the list and the detail page need to show it. */
data class MonitorPointRow(
    val settings: MonitorPointSettings,
    val appLabel: String,
    val operationLabel: String,
    /** False once the point has been unpicked; the settings are kept, not applied. */
    val watched: Boolean,
) {
    val packageName: String get() = settings.packageName
    val operationName: String get() = settings.operationName
}

/**
 * Lists the monitoring points and how each one is reported.
 *
 * This is its own page rather than part of picking the permissions, because it
 * answers a different question: picking decides what is watched, this decides
 * what each one is allowed to do when it fires. A point that is no longer
 * watched is kept under its own heading rather than removed, so unpicking a
 * permission does not quietly throw away settings that were chosen deliberately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorPointsScreen(
    points: List<MonitorPointRow>,
    onBack: () -> Unit,
    onPointSelected: (MonitorPointRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (watched, retained) = points.partition(MonitorPointRow::watched)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.monitor_points_title),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            item {
                Text(
                    text = stringResource(R.string.monitor_points_hint),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (points.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.monitor_points_empty),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(watched, key = { "${it.packageName}\n${it.operationName}" }) { point ->
                PointRow(point = point, onClick = { onPointSelected(point) })
            }
            // The kept settings are a different kind of row: nothing on them acts
            // until their point comes back, so they are separated rather than
            // greyed out in place.
            if (retained.isNotEmpty()) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                    Text(
                        text = stringResource(R.string.monitor_points_retained_header),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(retained, key = { "${it.packageName}\n${it.operationName}" }) { point ->
                    PointRow(point = point, onClick = { onPointSelected(point) })
                }
            }
        }
    }
}

@Composable
private fun PointRow(point: MonitorPointRow, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            AppIcon(packageName = point.packageName, appLabel = point.appLabel)
        },
        headlineContent = {
            Text(
                text = point.appLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (point.watched) Color.Unspecified else muted,
            )
        },
        supportingContent = {
            Text(
                text = "${point.operationLabel} · ${monitorPointSummary(point.settings)}",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = muted,
            )
        },
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = muted,
            )
        },
    )
}

/** The row has to say what was chosen without opening it. */
@Composable
private fun monitorPointSummary(settings: MonitorPointSettings): String {
    val parts = buildList {
        add(
            settings.throttleSeconds
                ?.let { stringResource(R.string.monitor_point_every, intervalText(it)) }
                ?: stringResource(R.string.monitor_point_every_access),
        )
        when (settings.headsUp) {
            true -> add(stringResource(R.string.monitor_alert_loud))
            false -> add(stringResource(R.string.monitor_alert_silent))
            null -> Unit
        }
        monitorOutcomesLabel(settings.outcomes, includeAll = false)?.let(::add)
        if (settings.backgroundOnly) add(stringResource(R.string.monitor_point_background_short))
    }
    return parts.joinToString(" · ")
}
