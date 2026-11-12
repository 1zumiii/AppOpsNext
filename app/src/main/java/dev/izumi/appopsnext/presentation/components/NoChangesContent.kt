package dev.izumi.appopsnext.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.izumi.appopsnext.R

/**
 * What a list shows while it keeps only the apps AppOpsNext changed and there
 * are none. [onShowAll] leaves the filter for the name order.
 */
@Composable
fun NoChangesContent(
    detail: String,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyStateContent(
        icon = R.drawable.ic_ph_note_pencil,
        title = stringResource(R.string.changes_empty_title),
        detail = detail,
        actionLabel = stringResource(R.string.changes_empty_show_all),
        onAction = onShowAll,
        modifier = modifier,
    )
}
