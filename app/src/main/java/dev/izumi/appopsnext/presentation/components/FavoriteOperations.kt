package dev.izumi.appopsnext.presentation.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MenuDefaults
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.presentation.app_detail.appOpDisplayLabel
import kotlinx.coroutines.delay
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.izumi.appopsnext.AppOpsNextApplication
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.appops.model.AppOpNames
import dev.izumi.appopsnext.settings.UserSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Splits a list into the operations marked as frequently used and the rest, keeping their order. */
object FavoriteOperations {
    fun isFavorite(favorites: Set<String>, operationName: String): Boolean =
        AppOpNames.stableName(operationName) in favorites

    fun <T> split(
        items: List<T>,
        favorites: Set<String>,
        operationName: (T) -> String,
    ): Pair<List<T>, List<T>> =
        if (favorites.isEmpty()) {
            emptyList<T>() to items
        } else {
            items.partition { isFavorite(favorites, operationName(it)) }
        }
}

/**
 * The frequently used operations, shared by every screen that lists
 * operations. [loaded] stays false until the settings are read, so the
 * one-time hint never flashes for someone who already dismissed it.
 */
class FavoriteOperationsState internal constructor(
    val favorites: Set<String>,
    private val hintSeen: Boolean,
    val loaded: Boolean,
    private val repository: UserSettingsRepository,
    private val scope: CoroutineScope,
) {
    val showHint: Boolean get() = loaded && !hintSeen && favorites.isEmpty()

    fun isFavorite(operationName: String): Boolean =
        FavoriteOperations.isFavorite(favorites, operationName)

    fun set(operationName: String, favorite: Boolean) {
        val stableName = AppOpNames.stableName(operationName)
        scope.launch {
            repository.setOperationFavorite(stableName, favorite)
            repository.setFavoriteHintSeen()
        }
    }

    /** The row moving to the other group is the feedback, so nothing else is shown. */
    fun toggle(operationName: String) = set(operationName, !isFavorite(operationName))

    fun dismissHint() {
        scope.launch { repository.setFavoriteHintSeen() }
    }
}

@Composable
fun rememberFavoriteOperations(): FavoriteOperationsState {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as AppOpsNextApplication }
    // A state flow held by the application, so a screen that opens again
    // starts from the current value rather than from nothing.
    val values by app.favoriteOperationsState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    return remember(values, app, scope) {
        FavoriteOperationsState(
            favorites = values?.operations.orEmpty(),
            hintSeen = values?.hintSeen ?: true,
            loaded = values != null,
            repository = app.userSettingsRepository,
            scope = scope,
        )
    }
}

/**
 * Wraps one operation row: a long press opens a menu that adds the operation
 * to frequently used or removes it, and screen readers get the same action.
 *
 * A row that opens something on a tap passes [onClick] and [shape]: its click
 * handler would otherwise take the press before the long press is seen, so
 * this one handler takes both. Without [onClick], taps still reach the
 * controls inside the row.
 */
@Composable
fun FavoriteOperationRow(
    operationName: String,
    state: FavoriteOperationsState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RectangleShape,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    // Open and position are kept apart: on closing, the menu fades out where it
    // was, so its position has to outlive the open flag.
    var menuOpen by remember { mutableStateOf(false) }
    var menuPosition by remember { mutableStateOf(Offset.Zero) }
    var rowHeight by remember { mutableStateOf(0) }
    val favorite = state.isFavorite(operationName)
    val actionLabel = stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add)
    val toggle = { state.toggle(operationName) }
    var lastPress by remember { mutableStateOf(Offset.Zero) }
    // While the menu is open, its row is lit a little so the menu reads as belonging to it.
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    Box(
        modifier = modifier
            .onSizeChanged { rowHeight = it.height }
            .drawWithContent {
                drawContent()
                if (menuOpen) {
                    drawOutline(shape.createOutline(size, layoutDirection, this), highlight)
                }
            }
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(shape)
                        .pointerInput(Unit) {
                            // Notes where the finger is, without taking the event, to open the menu there.
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    event.changes.firstOrNull()?.let { lastPress = it.position }
                                }
                            }
                        }
                        .combinedClickable(
                            onClick = onClick,
                            onLongClickLabel = actionLabel,
                            onLongClick = if (enabled) {
                                {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuPosition = lastPress
                                    menuOpen = true
                                }
                            } else {
                                null
                            },
                        )
                } else if (enabled) {
                    Modifier
                        .pointerInput(operationName) {
                            detectTapGestures(
                                onLongPress = { position ->
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuPosition = position
                                    menuOpen = true
                                },
                            )
                        }
                        .semantics {
                            customActions = listOf(
                                CustomAccessibilityAction(actionLabel) {
                                    toggle()
                                    true
                                },
                            )
                        }
                } else {
                    Modifier
                },
            ),
    ) {
        content()
        AppDropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            offset = with(density) {
                // The menu opens below the row's bottom edge; this moves it to the finger.
                DpOffset(menuPosition.x.toDp(), (menuPosition.y - rowHeight).toDp())
            },
        ) {
            AppDropdownMenuItem(
                text = actionLabel,
                iconRes = if (favorite) R.drawable.ic_ph_star_fill else R.drawable.ic_ph_star,
                onClick = {
                    menuOpen = false
                    toggle()
                },
            )
        }
    }
}

/**
 * Explains frequently used permissions once, in a bottom sheet, the first time
 * a screen that lists them opens. It waits a moment so the screen settles first.
 */
@Composable
fun FavoriteIntroSheet(state: FavoriteOperationsState) {
    if (!state.showHint) return
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(INTRO_DELAY_MILLIS)
        visible = true
    }
    if (!visible) return
    AppBottomSheet(
        onDismissRequest = state::dismissHint,
        title = { Text(text = stringResource(R.string.favorite_hint_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FavoriteIntroIllustration()
                Text(text = stringResource(R.string.favorite_intro_how))
                Text(
                    text = stringResource(R.string.favorite_intro_where),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = state::dismissHint) {
                Text(text = stringResource(R.string.action_got_it))
            }
        },
    )
}

private const val INTRO_DELAY_MILLIS = 500L

/** Two small panels drawn from the app's own rows: the long press, then the row at the top. */
@Composable
private fun FavoriteIntroIllustration() {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IntroPanel(
            caption = stringResource(R.string.favorite_intro_step_press),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            IntroRow("CAMERA", faded = true)
            Box {
                IntroRow("RECORD_AUDIO", pressed = true)
                IntroTouch(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp))
            }
            // The menu sits in its own slot below the pressed row, so nothing is drawn over it.
            // The same menu as the real one, at the panel's scale.
            Surface(
                modifier = Modifier.padding(start = 16.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shadowElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_ph_star),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.favorite_add),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Normal,
                        maxLines = 2,
                    )
                }
            }
        }
        IntroPanel(
            caption = stringResource(R.string.favorite_intro_step_top),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            IntroHeader(stringResource(R.string.favorite_section_title))
            IntroRow("RECORD_AUDIO", starred = true)
            IntroHeader(stringResource(R.string.favorite_section_rest))
            IntroRow("CAMERA", faded = true)
        }
    }
}

@Composable
private fun IntroPanel(
    caption: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content,
            )
        }
        Text(
            text = caption,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun IntroHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun IntroRow(
    operationName: String,
    pressed: Boolean = false,
    starred: Boolean = false,
    faded: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().alpha(if (faded) 0.5f else 1f),
        shape = RoundedCornerShape(10.dp),
        color = if (pressed) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.scale(22f / 28f)) { PermissionEntryIcon(operationName) }
            }
            Text(
                text = appOpDisplayLabel(operationName),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (starred) {
                Icon(
                    painter = painterResource(R.drawable.ic_ph_star_fill),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** A finger held down: a dot with a ring that keeps spreading out. */
@Composable
private fun IntroTouch(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "intro touch")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1400)),
        label = "intro ring",
    )
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.size(28.dp)) {
        val center = this.center
        drawCircle(
            color = color.copy(alpha = 0.35f * (1f - progress)),
            radius = size.minDimension / 2f * (0.45f + 0.55f * progress),
            center = center,
        )
        drawCircle(color = color.copy(alpha = 0.55f), radius = size.minDimension * 0.2f, center = center)
    }
}
