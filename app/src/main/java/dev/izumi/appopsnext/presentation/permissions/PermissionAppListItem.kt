package dev.izumi.appopsnext.presentation.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.appops.parser.EffectiveOpState
import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.presentation.app_detail.EditableModeMenu
import dev.izumi.appopsnext.presentation.app_detail.modeLabel
import dev.izumi.appopsnext.presentation.components.AppIcon

@Composable
internal fun PermissionAppListItem(
    app: InstalledApp,
    state: EffectiveOpState,
    runtimeGranted: Boolean?,
    editEnabled: Boolean,
    isApplying: Boolean,
    selectedForBatch: Boolean?,
    onSelectionChange: (Boolean) -> Unit,
    onModeSelected: (AppOpMode) -> Unit,
) {
    val selectable = selectedForBatch != null && editEnabled && state.mode != null
    // Like the app list, the whole row toggles selection while selecting.
    Surface(
        onClick = { onSelectionChange(selectedForBatch != true) },
        enabled = selectable,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectedForBatch != null) {
                Checkbox(
                    checked = selectedForBatch && state.mode != null,
                    onCheckedChange = onSelectionChange,
                    enabled = selectable,
                )
            } else {
                AppIcon(packageName = app.packageName, appLabel = app.label, size = 40.dp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = app.packageName,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(
                            if (state.scope == AppOpScope.UID) R.string.permission_scope_uid
                            else R.string.permission_scope_package,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (runtimeGranted != null && state.mode == AppOpMode.IGNORE) {
                    Text(
                        text = stringResource(
                            if (runtimeGranted) R.string.permission_runtime_granted
                            else R.string.permission_runtime_denied,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when {
                isApplying -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
                state.mode != null && selectedForBatch == null -> EditableModeMenu(
                    currentMode = state.mode,
                    enabled = editEnabled,
                    onModeSelected = { _, mode -> onModeSelected(mode) },
                )
                else -> Text(
                    text = state.mode?.let { modeLabel(it) } ?: state.rawMode,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
