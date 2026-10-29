package dev.izumi.appopsnext.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.center
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.hypot
import kotlin.math.max

/** Plays the light/dark switch as a circle that wipes the old look away. */
val LocalThemeTransition = staticCompositionLocalOf<ThemeTransition?> { null }

class ThemeTransition internal constructor(
    private val scope: CoroutineScope,
    private val layer: GraphicsLayer,
) {
    internal var snapshot by mutableStateOf<ImageBitmap?>(null)
        private set
    internal var origin by mutableStateOf(Offset.Unspecified)
        private set
    internal val progress = Animatable(0f)
    internal var currentDark by mutableStateOf(false)
    private var job: Job? = null

    /**
     * Freezes the screen, makes the change, then reveals the new look from
     * [origin], or the centre when it is unspecified. The new mode arrives
     * as a configuration change some frames later, so the reveal waits for it.
     */
    fun switchTo(dark: Boolean, origin: Offset = Offset.Unspecified, change: () -> Unit) {
        if (dark == currentDark || job?.isActive == true) {
            change()
            return
        }
        job = scope.launch {
            val image = runCatching { layer.toImageBitmap() }.getOrNull()
            if (image == null) {
                change()
                return@launch
            }
            this@ThemeTransition.origin = origin
            progress.snapTo(0f)
            snapshot = image
            try {
                change()
                withTimeoutOrNull(NEW_MODE_TIMEOUT_MILLIS) {
                    snapshotFlow { currentDark }.first { it == dark }
                }
                // Let the new colours reach the screen under the frozen frame.
                withFrameNanos {}
                withFrameNanos {}
                progress.animateTo(1f, tween(REVEAL_MILLIS, easing = FastOutSlowInEasing))
            } finally {
                snapshot = null
            }
        }
    }

    private companion object {
        const val NEW_MODE_TIMEOUT_MILLIS = 1_000L
        const val REVEAL_MILLIS = 450
    }
}

@Composable
internal fun ThemeTransitionHost(content: @Composable () -> Unit) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val transition = remember(layer) { ThemeTransition(scope, layer) }
    val dark = isSystemInDarkTheme()
    SideEffect { transition.currentDark = dark }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                },
        ) {
            CompositionLocalProvider(LocalThemeTransition provides transition, content = content)
        }
        transition.snapshot?.let { image ->
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    // Taps during the reveal would land on controls the user cannot see.
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    },
            ) {
                val center = if (transition.origin.isSpecified) transition.origin else size.center
                val reach = max(
                    max(hypot(center.x, center.y), hypot(size.width - center.x, center.y)),
                    max(hypot(center.x, size.height - center.y), hypot(size.width - center.x, size.height - center.y)),
                )
                val hole = Path().apply { addOval(Rect(center, reach * transition.progress.value)) }
                clipPath(hole, clipOp = ClipOp.Difference) {
                    drawImage(image)
                }
            }
        }
    }
}
