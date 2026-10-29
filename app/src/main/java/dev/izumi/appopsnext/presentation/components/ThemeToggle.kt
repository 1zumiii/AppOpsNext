package dev.izumi.appopsnext.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A light/dark switch whose thumb is a sun that a shadow bites into a crescent.
 *
 * Its colours are fixed rather than taken from the theme: the track shows the
 * mode it stands for, so it reads the same whichever mode is current.
 */
@Composable
fun ThemeToggle(
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (dark) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "themeToggle",
    )
    Canvas(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(width = 46.dp, height = 28.dp)
            .clip(RoundedCornerShape(50))
            .toggleable(value = dark, role = Role.Switch, onValueChange = onDarkChange)
            .semantics { this.contentDescription = contentDescription },
    ) {
        drawTrack(progress)
        drawThumb(progress)
    }
}

private fun DrawScope.drawTrack(progress: Float) {
    drawRoundRect(
        color = lerp(DayTrack, NightTrack, progress),
        cornerRadius = CornerRadius(size.height / 2),
    )
    // Two stars come out on the side the thumb has left.
    val starAlpha = ((progress - 0.5f) * 2f).coerceIn(0f, 1f)
    if (starAlpha > 0f) {
        val unit = size.height
        drawCircle(StarColor.copy(alpha = starAlpha), radius = unit * 0.05f, center = Offset(unit * 0.42f, unit * 0.34f))
        drawCircle(StarColor.copy(alpha = starAlpha * 0.7f), radius = unit * 0.035f, center = Offset(unit * 0.62f, unit * 0.64f))
    }
}

private fun DrawScope.drawThumb(progress: Float) {
    val inset = size.height * 0.18f
    val radius = size.height / 2 - inset
    val start = inset + radius
    val end = size.width - inset - radius
    val center = Offset(start + (end - start) * progress, size.height / 2)

    // The rays retract as the sun turns into the moon.
    val rayAlpha = 1f - (progress * 2f).coerceIn(0f, 1f)
    if (rayAlpha > 0f) {
        val inner = radius * 0.66f
        val outer = radius * (0.66f + 0.2f * rayAlpha)
        repeat(RAYS) { index ->
            val angle = 2 * PI * index / RAYS
            val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            drawLine(
                color = SunColor.copy(alpha = rayAlpha),
                start = center + direction * inner,
                end = center + direction * outer,
                strokeWidth = radius * 0.14f,
                cap = StrokeCap.Round,
            )
        }
    }

    val body = radius * (0.46f + 0.28f * progress)
    val disc = Path().apply { addOval(Rect(center, body)) }
    // The shadow slides in from the upper right to carve the crescent.
    val shadowCenter = center + Offset(body * (2.2f - 1.6f * progress), -body * (1.6f - 1.2f * progress))
    val shadow = Path().apply { addOval(Rect(shadowCenter, body * 0.82f)) }
    val shape = Path().apply { op(disc, shadow, PathOperation.Difference) }
    drawPath(shape, color = lerp(SunColor, MoonColor, progress))
}

private const val RAYS = 8
private val DayTrack = Color(0xFFFFE6B0)
private val NightTrack = Color(0xFF2C3242)
private val SunColor = Color(0xFFF5A300)
private val MoonColor = Color(0xFFF1EBD8)
private val StarColor = Color(0xFFF1EBD8)
