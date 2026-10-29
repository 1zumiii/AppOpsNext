package dev.izumi.appopsnext.presentation.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fades the top of a scrolled list, so cards leaving the top fade out instead
 * of being cut by the edge of the list. Nothing fades at the top of the list.
 */
fun Modifier.topFadingEdge(state: LazyListState, height: Dp = 16.dp): Modifier = this
    // The fade masks the list's own pixels, which needs a layer of its own.
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        if (state.canScrollBackward) {
            val fade = height.toPx()
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), endY = fade),
                size = Size(size.width, fade),
                blendMode = BlendMode.DstIn,
            )
        }
    }
