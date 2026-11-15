package dev.izumi.appopsnext.presentation.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
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
import kotlinx.coroutines.flow.map
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
    val snackbar: SnackbarHostState,
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

    /** Toggles [operationName] and offers to undo, since the row moves to the other group. */
    fun toggleWithUndo(operationName: String, addedMessage: String, removedMessage: String, undoLabel: String) {
        val favorite = !isFavorite(operationName)
        set(operationName, favorite)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = if (favorite) addedMessage else removedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) set(operationName, !favorite)
        }
    }

    fun dismissHint() {
        scope.launch { repository.setFavoriteHintSeen() }
    }
}

@Composable
fun rememberFavoriteOperations(): FavoriteOperationsState {
    val context = LocalContext.current
    val repository = remember(context) {
        (context.applicationContext as AppOpsNextApplication).userSettingsRepository
    }
    val flow = remember(repository) {
        repository.settings.map { it.favoriteOperations to it.favoriteHintSeen }
    }
    val values by flow.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    return remember(values, repository, scope, snackbar) {
        FavoriteOperationsState(
            favorites = values?.first.orEmpty(),
            hintSeen = values?.second ?: true,
            loaded = values != null,
            repository = repository,
            scope = scope,
            snackbar = snackbar,
        )
    }
}

/**
 * Wraps one operation row: a long press opens a menu that adds the operation
 * to frequently used or removes it, and screen readers get the same action.
 * Taps still reach the row and the controls inside it.
 */
@Composable
fun FavoriteOperationRow(
    operationName: String,
    state: FavoriteOperationsState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var menuOffset by remember { mutableStateOf<Offset?>(null) }
    var rowHeight by remember { mutableStateOf(0) }
    val favorite = state.isFavorite(operationName)
    val actionLabel = stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add)
    val added = stringResource(R.string.favorite_added)
    val removed = stringResource(R.string.favorite_removed)
    val undo = stringResource(R.string.favorite_undo)
    val toggle = { state.toggleWithUndo(operationName, added, removed, undo) }
    Box(
        modifier = modifier
            .onSizeChanged { rowHeight = it.height }
            .then(
                if (enabled) {
                    Modifier
                        .pointerInput(operationName) {
                            detectTapGestures(
                                onLongPress = { position ->
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuOffset = position
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
        val offset = menuOffset
        DropdownMenu(
            expanded = offset != null,
            onDismissRequest = { menuOffset = null },
            offset = with(density) {
                // The menu opens below the row's bottom edge; this moves it to the finger.
                offset?.let { DpOffset(it.x.toDp(), (it.y - rowHeight).toDp()) } ?: DpOffset.Zero
            },
        ) {
            DropdownMenuItem(
                text = { Text(text = actionLabel) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(if (favorite) R.drawable.ic_ph_star_fill else R.drawable.ic_ph_star),
                        contentDescription = null,
                    )
                },
                onClick = {
                    menuOffset = null
                    toggle()
                },
            )
        }
    }
}

/** Shows the one-time hint about long-pressing, anchored to [content]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteHintAnchor(
    state: FavoriteOperationsState,
    content: @Composable () -> Unit,
) {
    if (!state.showHint) {
        content()
        return
    }
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    LaunchedEffect(tooltipState) {
        // Returns once the hint is dismissed, by its button or a tap elsewhere.
        tooltipState.show()
        state.dismissHint()
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberRichTooltipPositionProvider(),
        tooltip = {
            RichTooltip(
                title = { Text(text = stringResource(R.string.favorite_hint_title)) },
                action = {
                    TextButton(onClick = { scope.launch { tooltipState.dismiss() } }) {
                        Text(text = stringResource(R.string.action_got_it))
                    }
                },
            ) {
                Text(text = stringResource(R.string.favorite_hint_text))
            }
        },
        state = tooltipState,
        enableUserInput = false,
    ) {
        content()
    }
}
