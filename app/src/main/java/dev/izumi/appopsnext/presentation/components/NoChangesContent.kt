package dev.izumi.appopsnext.presentation.components

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
                    painter = painterResource(R.drawable.ic_ph_note_pencil),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Text(
                text = stringResource(R.string.changes_empty_title),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            FilledTonalButton(
                onClick = onShowAll,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(text = stringResource(R.string.changes_empty_show_all))
            }
        }
    }
}
