package dev.izumi.appopsnext.presentation.experimental

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.monitor.MonitorOutcomes
import dev.izumi.appopsnext.monitor.MonitorThrottles

/**
 * Everything one monitoring point can be told to do.
 *
 * The three settings are the questions the monitor cannot answer for itself, so
 * they are answered per point: how often this application's use of this
 * permission is worth hearing about, how loudly, and which outcomes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorPointDetailScreen(
    point: MonitorPointRow,
    onBack: () -> Unit,
    onThrottleChange: (Int?) -> Unit,
    onHeadsUpChange: (Boolean?) -> Unit,
    onOutcomesChange: (MonitorOutcomes) -> Unit,
    onBackgroundOnlyChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val enabled = point.watched
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = point.appLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = point.operationLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                .padding(bottom = 16.dp),
        ) {
            if (!enabled) {
                Text(
                    text = stringResource(R.string.monitor_point_unwatched),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            ExperimentalGroup {
                ExperimentalGroupRow(
                    headlineContent = {
                        Text(text = stringResource(R.string.monitor_point_throttle_title))
                    },
                    supportingContent = {
                        Text(text = stringResource(R.string.monitor_point_throttle_summary))
                    },
                    trailingContent = {
                        Switch(
                            checked = point.settings.throttleSeconds != null,
                            enabled = enabled,
                            onCheckedChange = { on ->
                                // The field is about to be removed, so the keyboard it
                                // opened has to go with it.
                                focusManager.clearFocus()
                                onThrottleChange(if (on) MonitorThrottles.DEFAULT_SECONDS else null)
                            },
                        )
                    },
                )
                point.settings.throttleSeconds?.let { seconds ->
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    IntervalRow(seconds = seconds, enabled = enabled, onChange = onThrottleChange)
                }
            }
            Spacer(Modifier.height(12.dp))
            ExperimentalGroup {
                ChoiceRow(
                    title = stringResource(R.string.monitor_point_alert_title),
                    summary = stringResource(R.string.monitor_point_alert_summary),
                    selected = monitorAlertLabel(point.settings.headsUp),
                    enabled = enabled,
                    choices = listOf(
                        monitorAlertLabel(null) to null,
                        monitorAlertLabel(false) to false,
                        monitorAlertLabel(true) to true,
                    ),
                    onSelected = onHeadsUpChange,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ChoiceRow(
                    title = stringResource(R.string.monitor_point_outcome_title),
                    summary = stringResource(R.string.monitor_point_outcome_summary),
                    selected = monitorOutcomesLabel(point.settings.outcomes, includeAll = true)
                        ?: "",
                    enabled = enabled,
                    choices = MonitorOutcomes.entries.map { outcome ->
                        (monitorOutcomesLabel(outcome, includeAll = true) ?: "") to outcome
                    },
                    onSelected = onOutcomesChange,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ExperimentalGroupRow(
                    headlineContent = {
                        Text(text = stringResource(R.string.monitor_point_background_title))
                    },
                    supportingContent = {
                        Text(text = stringResource(R.string.monitor_point_background_summary))
                    },
                    trailingContent = {
                        Switch(
                            checked = point.settings.backgroundOnly,
                            enabled = enabled,
                            onCheckedChange = onBackgroundOnlyChange,
                        )
                    },
                )
            }
        }
    }
}

/** A title on the left and the control on the right, like every other row. */
@Composable
private fun <T> ChoiceRow(
    title: String,
    summary: String,
    selected: String,
    enabled: Boolean,
    choices: List<Pair<String, T>>,
    onSelected: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    ExperimentalGroupRow(
        headlineContent = { Text(text = title) },
        supportingContent = { Text(text = summary) },
        trailingContent = {
            Box {
                TextButton(onClick = { open = true }, enabled = enabled) {
                    Text(text = selected)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    choices.forEach { (label, value) ->
                        DropdownMenuItem(
                            text = { Text(text = label) },
                            onClick = {
                                open = false
                                onSelected(value)
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun IntervalRow(seconds: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    var minutes by remember(seconds) { mutableStateOf(intervalFieldDigits((seconds / 60).toString())) }
    var secondsField by remember(seconds) { mutableStateOf(intervalFieldDigits((seconds % 60).toString())) }

    val total = intervalFieldSeconds(minutes, secondsField)
    val valid = total != null && MonitorThrottles.isValid(total)

    fun edited(newMinutes: String, newSeconds: String) {
        minutes = newMinutes
        secondsField = newSeconds
        val updated = intervalFieldSeconds(newMinutes, newSeconds)
        if (updated != null && MonitorThrottles.isValid(updated)) onChange(updated)
    }

    ExperimentalGroupRow(
        headlineContent = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = stringResource(R.string.monitor_point_interval_title))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IntervalField(
                        value = minutes,
                        unit = stringResource(R.string.monitor_throttle_minutes),
                        enabled = enabled,
                        isError = !valid,
                        onValueChange = { edited(it, secondsField) },
                    )
                    IntervalField(
                        value = secondsField,
                        unit = stringResource(R.string.monitor_throttle_seconds),
                        enabled = enabled,
                        isError = !valid,
                        onValueChange = { edited(minutes, it) },
                    )
                }
                if (!valid) {
                    Text(
                        text = stringResource(
                            R.string.monitor_throttle_invalid,
                            MonitorThrottles.MAX_SECONDS / 60,
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
    )
}

/**
 * A compact bordered field.
 *
 * A text field with a floating label is built for a full-width form; two of them
 * in a row beside a title left the second with almost no width. This is the same
 * outline at the height a row can spare, with the unit inside it.
 */
@Composable
private fun IntervalField(
    value: String,
    unit: String,
    enabled: Boolean,
    isError: Boolean,
    onValueChange: (String) -> Unit,
) {
    val border = when {
        !enabled -> MaterialTheme.colorScheme.outlineVariant
        isError -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    val content = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .width(FIELD_WIDTH)
            .height(48.dp)
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            // Anything that is not a digit cannot be part of an interval, and a
            // run of leading zeros is the same number written longer, so neither
            // ever reaches the stored value or the box.
            onValueChange = { typed -> onValueChange(intervalFieldDigits(typed)) },
            modifier = Modifier.weight(1f),
            enabled = enabled,
            singleLine = true,
            textStyle = LocalTextStyle.current.merge(
                MaterialTheme.typography.bodyLarge.copy(color = content),
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) { field() }
            },
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun monitorAlertLabel(headsUp: Boolean?): String = when (headsUp) {
    null -> stringResource(R.string.monitor_alert_default)
    false -> stringResource(R.string.monitor_alert_silent)
    true -> stringResource(R.string.monitor_alert_loud)
}

/** Null when the choice is "everything" and the caller only wants what was narrowed. */
@Composable
internal fun monitorOutcomesLabel(outcomes: MonitorOutcomes, includeAll: Boolean): String? =
    when (outcomes) {
        MonitorOutcomes.ALL ->
            if (includeAll) stringResource(R.string.monitor_outcome_all) else null
        MonitorOutcomes.REFUSED -> stringResource(R.string.monitor_outcome_refused)
        MonitorOutcomes.ALLOWED -> stringResource(R.string.monitor_outcome_allowed)
    }

/** `1 分 30 秒`, or just the part that is not zero. */
@Composable
internal fun intervalText(seconds: Int): String {
    val minutes = seconds / 60
    val rest = seconds % 60
    return when {
        minutes == 0 -> stringResource(R.string.monitor_interval_seconds, rest)
        rest == 0 -> stringResource(R.string.monitor_interval_minutes, minutes)
        else -> stringResource(R.string.monitor_interval_both, minutes, rest)
    }
}

/** Digits only, without a run of leading zeros, and short enough to mean something. */
internal fun intervalFieldDigits(text: String): String {
    val kept = text.filter(Char::isDigit).take(MAX_FIELD_DIGITS)
    val trimmed = kept.trimStart('0')
    return when {
        trimmed.isNotEmpty() -> trimmed
        kept.isNotEmpty() -> "0"
        else -> ""
    }
}

/** Null when a field is not a number, which happens while one is being retyped. */
internal fun intervalFieldSeconds(minutes: String, seconds: String): Int? {
    val m = minutes.ifEmpty { "0" }.toIntOrNull() ?: return null
    val s = seconds.ifEmpty { "0" }.toIntOrNull() ?: return null
    return m * 60 + s
}

private val FIELD_WIDTH = 104.dp
private const val MAX_FIELD_DIGITS = 4
