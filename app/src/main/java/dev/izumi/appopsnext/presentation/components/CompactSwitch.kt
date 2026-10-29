package dev.izumi.appopsnext.presentation.components

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale

/** The app's switch, a size smaller than Material's so it sits lighter beside list text. */
@Composable
fun CompactSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.scale(SCALE),
        enabled = enabled,
    )
}

private const val SCALE = 0.8f
