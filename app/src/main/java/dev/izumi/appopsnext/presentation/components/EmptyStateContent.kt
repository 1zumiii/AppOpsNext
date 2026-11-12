package dev.izumi.appopsnext.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R

/**
 * What a page or a whole list shows when it has nothing to show: an icon in a
 * round tonal badge, a short [title], an optional [detail] and an optional
 * button. The content is centred in whatever space [modifier] gives it.
 *
 * A search or filter with no results takes [EmptyStateIcons.NO_MATCHES] and a
 * title of its own, so it never reads like a page that has no data at all.
 */
@Composable
fun EmptyStateContent(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier = modifier.padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Text(
                text = title,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (actionLabel != null && onAction != null) {
                FilledTonalButton(
                    onClick = onAction,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(text = actionLabel)
                }
            }
        }
    }
}

/** Icons shared by more than one empty state. */
object EmptyStateIcons {
    /** A search or filter that matched nothing. */
    @DrawableRes
    val NO_MATCHES: Int = R.drawable.ic_ph_magnifying_glass

    /** Something could not be read. */
    @DrawableRes
    val LOAD_FAILED: Int = R.drawable.ic_ph_warning_circle
}
