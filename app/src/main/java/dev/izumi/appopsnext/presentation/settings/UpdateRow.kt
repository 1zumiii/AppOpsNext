package dev.izumi.appopsnext.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.BuildConfig
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.presentation.components.MainPageEntryIcon
import dev.izumi.appopsnext.update.UpdateState

/**
 * The app version, with the update result alongside it.
 *
 * A new version is also listed in the in-app notifications and marked with a
 * dot on the About entry. It never interrupts with a dialog or a system
 * notification.
 */
@Composable
fun AppVersionRow(
    updateState: UpdateState,
    /** Whether the version is marked with a dot: an update the user has not ignored. */
    showDot: Boolean,
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
        leadingContent = {
            MainPageEntryIcon(R.drawable.ic_ph_info)
        },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(R.string.settings_app_version))
                if (showDot) {
                    Box(modifier = Modifier.width(6.dp))
                    UpdateDot(Modifier.size(8.dp))
                }
            }
        },
        supportingContent = {
            Text(
                text = stringResource(
                    R.string.settings_app_version_value,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE,
                ),
                softWrap = false,
            )
        },
        trailingContent = { UpdateStatus(updateState) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun UpdateStatus(state: UpdateState) {
    when (state) {
        is UpdateState.Available -> {
            Text(
                text = stringResource(R.string.update_available, state.versionName),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        UpdateState.Checking -> StatusLabel(stringResource(R.string.update_checking))
        UpdateState.UpToDate -> StatusLabel(
            stringResource(R.string.update_up_to_date),
            stringResource(R.string.update_check_again),
        )

        UpdateState.Failed -> StatusLabel(
            stringResource(R.string.update_failed),
            stringResource(R.string.update_retry),
        )
        UpdateState.Idle -> Unit
    }
}

/** The result on one line and what a tap does on the next, so neither wraps mid-phrase. */
@Composable
private fun StatusLabel(text: String, action: String? = null) {
    Column(
        modifier = Modifier.widthIn(max = 150.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = text,
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null) {
            Text(
                text = action,
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** The amber dot that marks an available update, read out as "A new version is available". */
@Composable
fun UpdateDot(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.update_available_description)
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(UpdateDotColor)
            .semantics { contentDescription = description },
    )
}

/** Amber reads as "worth a look" without the alarm of the error colour. */
private val UpdateDotColor = Color(0xFFFFB300)
