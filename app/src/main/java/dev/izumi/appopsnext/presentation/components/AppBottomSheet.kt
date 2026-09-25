package dev.izumi.appopsnext.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.ui.theme.mainPageHeadingWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismissRequest: () -> Unit,
    title: (@Composable () -> Unit)? = null,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    leadingButton: (@Composable () -> Unit)? = null,
    dismissible: Boolean = true,
    showActions: Boolean = true,
    bodyHorizontalPadding: Dp = 24.dp,
) {
    val canDismiss by rememberUpdatedState(dismissible)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { canDismiss || it != SheetValue.Hidden },
    )
    ModalBottomSheet(
        onDismissRequest = { if (canDismiss) onDismissRequest() },
        sheetState = sheetState,
        sheetMaxWidth = Dp.Unspecified,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = if (dismissible) {
            { BottomSheetDefaults.DragHandle() }
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
        ) {
            if (title != null) {
                ProvideTextStyle(
                    MaterialTheme.typography.titleLarge.copy(
                        fontWeight = mainPageHeadingWeight(),
                    ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 16.dp),
                    ) {
                        title()
                    }
                }
            }
            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(horizontal = bodyHorizontalPadding),
                ) {
                    text()
                }
            }
            if (showActions) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    if (leadingButton != null) {
                        leadingButton()
                        Spacer(Modifier.weight(1f))
                    }
                    dismissButton?.invoke()
                    confirmButton()
                }
            } else {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
