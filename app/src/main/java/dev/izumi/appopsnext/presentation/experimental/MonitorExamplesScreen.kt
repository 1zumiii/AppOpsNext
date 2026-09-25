package dev.izumi.appopsnext.presentation.experimental

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R

/**
 * What the per-point settings are for, written as situations rather than
 * options.
 *
 * The settings exist because the monitor cannot judge these cases for itself,
 * which also means the page that holds them cannot explain what they are for:
 * "report every access" says what the switch does, not when you would want it.
 * These are read-only on purpose — a situation belongs to an application the
 * reader has in mind, and applying one for them would be guessing at which.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorExamplesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val examples = listOf(
        R.string.monitor_example_own_use to R.string.monitor_example_own_use_body,
        R.string.monitor_example_quiet_location to R.string.monitor_example_quiet_location_body,
        R.string.monitor_example_sensors to R.string.monitor_example_sensors_body,
        R.string.monitor_example_refusals to R.string.monitor_example_refusals_body,
        R.string.monitor_example_one_shot to R.string.monitor_example_one_shot_body,
        R.string.monitor_example_unknown to R.string.monitor_example_unknown_body,
    )
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.monitor_examples_title),
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            item {
                Text(
                    text = stringResource(R.string.monitor_examples_hint),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(examples, key = { it.first }) { (title, body) ->
                ExperimentalListRow(
                    headlineContent = {
                        Text(
                            text = stringResource(title),
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    supportingContent = { Text(text = stringResource(body)) },
                )
            }
        }
    }
}
