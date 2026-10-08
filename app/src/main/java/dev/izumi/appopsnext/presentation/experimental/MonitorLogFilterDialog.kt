package dev.izumi.appopsnext.presentation.experimental

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.presentation.components.AppBottomSheet
import dev.izumi.appopsnext.presentation.components.CompactSearchField

internal data class MonitorFilterOption(val key: String, val label: String)

@Composable
internal fun MonitorLogFilterDialog(
    title: String,
    searchLabel: String,
    allLabel: String,
    options: List<MonitorFilterOption>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = options.filter {
        it.label.contains(query.trim(), ignoreCase = true) ||
            it.key.contains(query.trim(), ignoreCase = true)
    }
    AppBottomSheet(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                CompactSearchField(query, { query = it }, searchLabel)
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    item {
                        ListItem(
                            modifier = Modifier.clickable { onSelect(null) },
                            headlineContent = { Text(allLabel) },
                            trailingContent = { RadioButton(selected == null, { onSelect(null) }) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                    if (shown.isEmpty()) {
                        item { Text(stringResource(R.string.filter_no_matches)) }
                    }
                    items(shown, key = { it.key }) { option ->
                        ListItem(
                            modifier = Modifier.clickable { onSelect(option.key) },
                            headlineContent = { Text(option.label) },
                            supportingContent = { Text(option.key) },
                            trailingContent = {
                                RadioButton(selected == option.key, { onSelect(option.key) })
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
