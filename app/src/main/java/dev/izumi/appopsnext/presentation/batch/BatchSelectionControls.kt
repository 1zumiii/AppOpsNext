package dev.izumi.appopsnext.presentation.batch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R

@Composable
fun BatchSelectionControls(
    selectedCount: Int,
    visibleSelectedCount: Int,
    visibleItemCount: Int,
    onToggleAll: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    actions: @Composable RowScope.() -> Unit,
) {
    val selectionState = when {
        visibleSelectedCount == 0 -> ToggleableState.Off
        visibleSelectedCount == visibleItemCount -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    val selectionEnabled = enabled && visibleItemCount > 0
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .triStateToggleable(
                            state = selectionState,
                            enabled = selectionEnabled,
                            role = Role.Checkbox,
                            onClick = onToggleAll,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TriStateCheckbox(
                        state = selectionState,
                        onClick = null,
                        enabled = selectionEnabled,
                    )
                    Text(
                        text = stringResource(R.string.selection_select_all),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Text(
                    text = stringResource(R.string.batch_selected_count, selectedCount),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}
