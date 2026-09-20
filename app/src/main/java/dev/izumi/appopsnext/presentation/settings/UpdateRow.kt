package dev.izumi.appopsnext.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.BuildConfig
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.update.UpdateState

/**
 * The app version, with the update result alongside it.
 *
 * A new version is shown here and nowhere else. Interrupting someone on launch
 * to tell them about an update is the behaviour this deliberately avoids.
 */
@Composable
fun AppVersionRow(
    updateState: UpdateState,
    onOpenRelease: (String) -> Unit,
    onCheckForUpdate: () -> Unit,
) {
    val clickAction: (() -> Unit)? = when (updateState) {
        is UpdateState.Available -> {
            { onOpenRelease(updateState.releaseUrl) }
        }

        UpdateState.UpToDate, UpdateState.Failed -> onCheckForUpdate
        else -> null
    }
    ListItem(
        modifier = clickAction?.let { Modifier.clickable(onClick = it) } ?: Modifier,
        headlineContent = {
            Text(text = stringResource(R.string.settings_app_version))
        },
        supportingContent = {
            Text(
                text = stringResource(
                    R.string.settings_app_version_value,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE,
                ),
            )
        },
        trailingContent = { UpdateStatus(updateState) },
    )
}

@Composable
private fun UpdateStatus(state: UpdateState) {
    when (state) {
        is UpdateState.Available -> {
            val description = stringResource(R.string.update_available_description)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(UpdateDotColor)
                        .semantics { contentDescription = description },
                )
                Box(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.update_available, state.versionName),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        UpdateState.Checking -> StatusLabel(stringResource(R.string.update_checking))
        UpdateState.UpToDate -> StatusLabel(stringResource(R.string.update_up_to_date))
        UpdateState.Failed -> StatusLabel(stringResource(R.string.update_failed))
        UpdateState.Idle -> Unit
    }
}

@Composable
private fun StatusLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Amber reads as "worth a look" without the alarm of the error colour. */
private val UpdateDotColor = Color(0xFFFFB300)
