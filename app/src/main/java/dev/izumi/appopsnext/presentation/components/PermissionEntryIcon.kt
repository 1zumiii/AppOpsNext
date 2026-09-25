package dev.izumi.appopsnext.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import dev.izumi.appopsnext.presentation.history.HistoryPermissionIconCatalog
import dev.izumi.appopsnext.presentation.history.HistoryPermissionTone

@Composable
fun PermissionEntryIcon(operationName: String) {
    val visual = HistoryPermissionIconCatalog.visualFor(operationName)
    val (containerColor, iconColor) = when (visual.tone) {
        HistoryPermissionTone.PRIMARY ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        HistoryPermissionTone.SECONDARY ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.secondary
        HistoryPermissionTone.TERTIARY ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.tertiary
        HistoryPermissionTone.NEUTRAL ->
            MaterialTheme.colorScheme.surfaceContainerHighest to
                MaterialTheme.colorScheme.onSurfaceVariant
    }
    MainPageEntryIcon(
        iconRes = visual.iconRes,
        containerColor = containerColor,
        contentColor = iconColor,
    )
}
