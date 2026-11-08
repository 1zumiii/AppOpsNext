package dev.izumi.appopsnext.presentation.app_detail

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.appops.changes.AppOpChangeStatus

/** One line on how a setting compares with the change AppOpsNext last made to it. */
@Composable
internal fun AppOpChangeStatusText(
    status: AppOpChangeStatus,
    modifier: Modifier = Modifier,
) {
    when (status) {
        is AppOpChangeStatus.Modified -> Text(
            text = stringResource(R.string.app_op_change_modified),
            modifier = modifier,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall,
        )
        is AppOpChangeStatus.ChangedElsewhere -> Text(
            text = stringResource(
                if (status.currentMode == null) R.string.app_op_change_elsewhere_unknown
                else R.string.app_op_change_elsewhere,
                modeLabel(status.record.recordedMode),
            ),
            modifier = modifier,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
