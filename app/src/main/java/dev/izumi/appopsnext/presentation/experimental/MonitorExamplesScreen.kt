package dev.izumi.appopsnext.presentation.experimental

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R

/** Read-only situations and their suggested settings; no example applies a configuration. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorExamplesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val examples = listOf(
        MonitorExample(
            R.string.monitor_example_own_use,
            R.string.monitor_example_own_use_body,
            R.drawable.ic_ph_map_pin,
            listOf(
                R.string.monitor_point_background_short,
                R.string.monitor_example_minute_or_more,
                R.string.monitor_alert_silent,
            ),
        ),
        MonitorExample(
            R.string.monitor_example_quiet_location,
            R.string.monitor_example_quiet_location_body,
            R.drawable.ic_ph_eye,
            listOf(
                R.string.monitor_point_background_short,
                R.string.monitor_example_every_minute,
                R.string.monitor_alert_loud,
            ),
        ),
        MonitorExample(
            R.string.monitor_example_sensors,
            R.string.monitor_example_sensors_body,
            R.drawable.ic_ph_camera,
            listOf(
                R.string.monitor_point_background_short,
                R.string.monitor_example_no_limit,
                R.string.monitor_alert_loud,
            ),
        ),
        MonitorExample(
            R.string.monitor_example_refusals,
            R.string.monitor_example_refusals_body,
            R.drawable.ic_ph_shield,
            listOf(
                R.string.monitor_outcome_refused,
                R.string.monitor_example_no_limit,
                R.string.monitor_alert_loud,
            ),
        ),
        MonitorExample(
            R.string.monitor_example_one_shot,
            R.string.monitor_example_one_shot_body,
            R.drawable.ic_ph_clipboard_text,
            listOf(
                R.string.monitor_example_no_limit,
                R.string.monitor_example_on_screen_too,
            ),
        ),
        MonitorExample(
            R.string.monitor_example_unknown,
            R.string.monitor_example_unknown_body,
            R.drawable.ic_ph_info,
            listOf(
                R.string.monitor_example_every_30_seconds,
                R.string.monitor_alert_silent,
            ),
        ),
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
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.monitor_examples_hint),
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            itemsIndexed(examples, key = { _, example -> example.titleRes }) { index, example ->
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(example.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(example.titleRes),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = stringResource(example.bodyRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        example.settingRes.forEach { labelRes ->
                            ExampleSettingChip(stringResource(labelRes))
                        }
                    }
                }
                if (index < examples.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

private data class MonitorExample(
    val titleRes: Int,
    val bodyRes: Int,
    val iconRes: Int,
    val settingRes: List<Int>,
)

@Composable
private fun ExampleSettingChip(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
