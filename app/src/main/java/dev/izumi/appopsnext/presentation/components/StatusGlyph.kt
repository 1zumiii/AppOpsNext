package dev.izumi.appopsnext.presentation.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R

enum class StatusVisual {
    NEUTRAL,
    SUCCESS,
    WARNING,
    ERROR,
}

@Composable
fun StatusGlyph(status: StatusVisual, modifier: Modifier = Modifier) {
    val iconRes = when (status) {
        StatusVisual.NEUTRAL -> R.drawable.ic_action_info
        StatusVisual.SUCCESS -> R.drawable.ic_status_check
        StatusVisual.WARNING, StatusVisual.ERROR -> R.drawable.ic_status_alert
    }
    val dark = isSystemInDarkTheme()
    val color = when (status) {
        StatusVisual.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
        StatusVisual.SUCCESS -> if (dark) Color(0xFF85D7A5) else Color(0xFF20774A)
        StatusVisual.WARNING -> if (dark) Color(0xFFFFD182) else Color(0xFF926300)
        StatusVisual.ERROR -> MaterialTheme.colorScheme.error
    }
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = modifier,
        tint = color,
    )
}

@Composable
fun StatusGlyphBlock(status: StatusVisual) {
    Surface(
        modifier = Modifier.size(44.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Box(contentAlignment = Alignment.Center) {
            StatusGlyph(status = status, modifier = Modifier.size(22.dp))
        }
    }
}
