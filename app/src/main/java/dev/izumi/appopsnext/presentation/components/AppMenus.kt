package dev.izumi.appopsnext.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R

/**
 * The app's small menu, opened next to what it acts on. It sits lightly on
 * the cards: rounded like them, one tone lighter, with a soft shadow.
 */
@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        content = content,
    )
}

/**
 * One entry of [AppDropdownMenu]. Text keeps a regular weight and icons stay
 * grey. The [selected] entry, the current value, carries a check in the
 * accent colour, the only place a menu uses it.
 */
@Composable
fun AppDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    supportingText: String? = null,
    @DrawableRes iconRes: Int? = null,
    enabled: Boolean = true,
) {
    DropdownMenuItem(
        text = {
            Column {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal,
                )
                supportingText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        onClick = onClick,
        modifier = modifier.semantics { this.selected = selected },
        leadingIcon = iconRes?.let {
            {
                Icon(painter = painterResource(it), contentDescription = null, modifier = Modifier.size(20.dp))
            }
        },
        trailingIcon = if (selected) {
            {
                Icon(
                    painter = painterResource(R.drawable.ic_ph_check),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            null
        },
        enabled = enabled,
        colors = MenuDefaults.itemColors(
            textColor = MaterialTheme.colorScheme.onSurface,
            leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            trailingIconColor = MaterialTheme.colorScheme.primary,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
    )
}

/** Sets apart a group of entries in [AppDropdownMenu]. */
@Composable
fun AppDropdownMenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
