package dev.izumi.appopsnext.presentation.experimental

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import dev.izumi.appopsnext.R

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
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenTargets),
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
                )
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenPoints),
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
                )
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenExamples),
                    headlineContent = {
                        Text(text = stringResource(R.string.monitor_examples_entry))
                    },
                    supportingContent = {
                        Text(text = stringResource(R.string.monitor_examples_summary))
                    },
                )
            }
            item {
                ListItem(
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
