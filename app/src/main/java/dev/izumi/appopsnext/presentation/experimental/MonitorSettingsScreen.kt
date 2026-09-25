package dev.izumi.appopsnext.presentation.experimental

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.presentation.components.MainPageChevron

/**
 * Everything that configures the monitor, gathered behind one entry.
 *
 * The entry to this page is what gets closed while the monitor runs, so the
 * individual settings do not each have to explain that they cannot be changed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorSettingsScreen(
    uiState: ExperimentalUiState,
    onBack: () -> Unit,
    onOpenTargets: () -> Unit,
    onOpenPoints: () -> Unit,
    onOpenExamples: () -> Unit,
    onHeadsUpChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.monitor_settings_title),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp, bottom = 16.dp),
        ) {
            ExperimentalGroup {
                ExperimentalGroupRow(
                    modifier = Modifier.clickable(onClick = onOpenTargets),
                    leadingContent = { ExperimentalRowIcon(R.drawable.ic_ph_user) },
                    headlineContent = {
                        Text(text = stringResource(R.string.monitor_targets_title))
                    },
                    supportingContent = {
                        Text(
                            text = if (uiState.targetCount == 0) {
                                stringResource(R.string.monitor_targets_empty)
                            } else {
                                stringResource(
                                    R.string.monitor_targets_summary,
                                    uiState.targetCount,
                                )
                            },
                        )
                    },
                    trailingContent = { MainPageChevron() },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                ExperimentalGroupRow(
                    modifier = Modifier.clickable(onClick = onOpenPoints),
                    leadingContent = { ExperimentalRowIcon(R.drawable.ic_ph_list_checks) },
                    headlineContent = {
                        Text(text = stringResource(R.string.monitor_points_title))
                    },
                    supportingContent = {
                        Text(
                            text = if (uiState.points.isEmpty()) {
                                stringResource(R.string.monitor_points_none)
                            } else {
                                stringResource(
                                    R.string.monitor_points_summary,
                                    uiState.points.size,
                                )
                            },
                        )
                    },
                    trailingContent = { MainPageChevron() },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                ExperimentalGroupRow(
                    modifier = Modifier.clickable(onClick = onOpenExamples),
                    leadingContent = { ExperimentalRowIcon(R.drawable.ic_ph_info) },
                    headlineContent = {
                        Text(text = stringResource(R.string.monitor_examples_entry))
                    },
                    supportingContent = {
                        Text(text = stringResource(R.string.monitor_examples_summary))
                    },
                    trailingContent = { MainPageChevron() },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                ExperimentalGroupRow(
                    leadingContent = { ExperimentalRowIcon(R.drawable.ic_ph_gear) },
                    headlineContent = {
                        Text(text = stringResource(R.string.monitor_heads_up_title))
                    },
                    supportingContent = {
                        Text(text = stringResource(R.string.monitor_heads_up_summary))
                    },
                    trailingContent = {
                        Switch(
                            checked = uiState.headsUp,
                            onCheckedChange = onHeadsUpChange,
                        )
                    },
                )
            }
        }
    }
}
