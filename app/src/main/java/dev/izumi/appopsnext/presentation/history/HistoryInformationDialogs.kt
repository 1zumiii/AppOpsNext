package dev.izumi.appopsnext.presentation.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.presentation.components.AppBottomSheet
import dev.izumi.appopsnext.history.model.HistoryPermission
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryOverviewInformationDialog(
    uiState: HistoryUiState,
    onDismiss: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
        ?: Locale.getDefault()
    val lastUpdated = uiState.lastUpdatedAtMillis?.let { timestamp ->
        DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.SHORT,
            locale,
        ).format(Date(timestamp))
    } ?: stringResource(R.string.history_never_updated)

    HistoryInformationDialog(
        title = stringResource(R.string.history_information),
        paragraphs = listOf(stringResource(R.string.history_subtitle)) +
            historyCaveatParagraphs(includeChart = false) + listOf(
            stringResource(R.string.history_reorder_permissions),
            stringResource(
                R.string.history_auto_refresh_summary,
                uiState.autoRefreshIntervalMinutes,
                lastUpdated,
            ),
        ),
        onDismiss = onDismiss,
    )
}

@Composable
fun PermissionHistoryInformationDialog(
    permission: HistoryPermission,
    onDismiss: () -> Unit,
) {
    HistoryInformationDialog(
        title = permission.displayName(),
        paragraphs = listOf(permission.systemOperationName()) + historyCaveatParagraphs(),
        onDismiss = onDismiss,
    )
}

/** One topic per paragraph, so each can be found at a glance. */
@Composable
private fun historyCaveatParagraphs(includeChart: Boolean = true): List<String> =
    listOf(
        stringResource(R.string.history_caveat_individual),
        stringResource(R.string.history_caveat_intervals),
        stringResource(R.string.history_caveat_denials),
        stringResource(R.string.history_caveat_range),
    ) + if (includeChart) listOf(stringResource(R.string.history_caveat_chart)) else emptyList()

@Composable
private fun HistoryInformationDialog(
    title: String,
    paragraphs: List<String>,
    onDismiss: () -> Unit,
) {
    AppBottomSheet(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                paragraphs.forEach { paragraph ->
                    Text(text = paragraph)
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
