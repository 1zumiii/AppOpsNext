package dev.izumi.appopsnext.presentation.permissions

import android.text.format.DateUtils
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.appops.changes.AppOpChangeStatus
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.parser.EffectiveOpState
import dev.izumi.appopsnext.apps.AppSortOrder
import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.presentation.app_detail.AppOpChangeStatusText
import dev.izumi.appopsnext.presentation.app_detail.EditableModeMenu
import dev.izumi.appopsnext.presentation.app_detail.modeLabel
import dev.izumi.appopsnext.presentation.components.AppIcon

/**
 * The one line under an app's name, picked by what matters most on this page:
 * a change made elsewhere, then the time the list is sorted by, then the
 * runtime permission behind an ignored operation, then that AppOpsNext
 * changed the setting. Everything else is in the app's details.
 */
internal sealed interface PermissionRowStatus {
    data class ChangedElsewhere(val status: AppOpChangeStatus.ChangedElsewhere) : PermissionRowStatus
    data class SortTime(val order: AppSortOrder, val millis: Long) : PermissionRowStatus
    data class Runtime(val granted: Boolean) : PermissionRowStatus
    data object Modified : PermissionRowStatus

    companion object {
        fun pick(
            app: InstalledApp,
            order: AppSortOrder,
            /** When AppOpsNext last changed this setting, if it did. */
            changedMillis: Long?,
            changeStatus: AppOpChangeStatus?,
            mode: AppOpMode?,
            runtimeGranted: Boolean?,
        ): PermissionRowStatus? {
            if (changeStatus is AppOpChangeStatus.ChangedElsewhere) return ChangedElsewhere(changeStatus)
            val sortMillis = when (order) {
                AppSortOrder.NAME -> null
                AppSortOrder.INSTALL_TIME -> app.firstInstallTimeMillis
                AppSortOrder.UPDATE_TIME -> app.lastUpdateTimeMillis
                AppSortOrder.CHANGED_ONLY -> changedMillis
            }
            if (sortMillis != null) return SortTime(order, sortMillis)
            if (runtimeGranted != null && mode == AppOpMode.IGNORE) return Runtime(runtimeGranted)
            if (changeStatus is AppOpChangeStatus.Modified) return Modified
            return null
        }
    }
}

@Composable
internal fun PermissionAppListItem(
    app: InstalledApp,
    /** The operation every row shows, which decides the modes on offer. */
    operation: String,
    state: EffectiveOpState,
    status: PermissionRowStatus?,
    /** Shown only where another app on the list has the same name. */
    showPackageName: Boolean,
    editEnabled: Boolean,
    isApplying: Boolean,
    selectedForBatch: Boolean?,
    onSelectionChange: (Boolean) -> Unit,
    onModeSelected: (AppOpMode) -> Unit,
    /** Opens the app's details, where its package, UID and change history are. */
    onOpen: () -> Unit,
) {
    val selecting = selectedForBatch != null
    val selectable = selecting && editEnabled && state.mode != null
    // While selecting, the whole row toggles selection, as in the app list.
    // Otherwise it opens the app.
    Surface(
        onClick = { if (selecting) onSelectionChange(selectedForBatch != true) else onOpen() },
        enabled = if (selecting) selectable else !isApplying,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) {
                Checkbox(
                    checked = selectedForBatch == true && state.mode != null,
                    onCheckedChange = onSelectionChange,
                    enabled = selectable,
                )
            } else {
                AppIcon(
                    packageName = app.packageName,
                    appLabel = app.label,
                    size = 40.dp,
                    userId = app.userId,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showPackageName) {
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                status?.let { PermissionRowStatusText(it) }
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
                    modes = AppOpMode.choicesFor(operation, state.mode),
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

@Composable
private fun PermissionRowStatusText(status: PermissionRowStatus) {
    val style = MaterialTheme.typography.bodySmall
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    when (status) {
        is PermissionRowStatus.ChangedElsewhere -> AppOpChangeStatusText(status.status)
        is PermissionRowStatus.SortTime -> {
            val relative = remember(status.millis) {
                DateUtils.getRelativeTimeSpanString(
                    status.millis,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE,
                ).toString()
            }
            val label = when (status.order) {
                AppSortOrder.CHANGED_ONLY -> R.string.permission_row_changed_by
                AppSortOrder.INSTALL_TIME -> R.string.app_list_installed_at
                else -> R.string.app_list_updated_at
            }
            Text(text = stringResource(label, relative), style = style, color = quiet, maxLines = 1)
        }
        is PermissionRowStatus.Runtime -> Text(
            text = stringResource(
                if (status.granted) R.string.permission_runtime_granted else R.string.permission_runtime_denied,
            ),
            style = style,
            color = quiet,
        )
        PermissionRowStatus.Modified -> Text(
            text = stringResource(R.string.app_op_change_modified),
            style = style,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
