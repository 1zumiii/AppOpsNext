package dev.izumi.appopsnext.presentation.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.apps.AppSortOrder
import dev.izumi.appopsnext.apps.model.InstalledApp

/** The sort button of every list of apps. They share one order, kept in the settings. */
@Composable
fun AppSortMenu(
    selected: AppSortOrder,
    onSelected: (AppSortOrder) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_ph_sort_descending),
                contentDescription = stringResource(R.string.app_list_sort),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            AppSortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(text = stringResource(order.labelRes())) },
                    onClick = {
                        open = false
                        onSelected(order)
                    },
                    trailingIcon = if (order == selected) {
                        {
                            Icon(
                                painter = painterResource(R.drawable.ic_ph_check_circle),
                                contentDescription = null,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/**
 * A list keeps its first visible app in view when the order changes, which lands
 * in the middle of the new order. A new order starts from the top instead. The
 * order last listed is saved, so coming back to the list or rotating keeps the
 * position.
 */
@Composable
fun ScrollToTopOnSortChange(order: AppSortOrder, listState: LazyListState) {
    var listedOrder by rememberSaveable { mutableStateOf(order) }
    LaunchedEffect(order) {
        if (order != listedOrder) {
            listedOrder = order
            listState.scrollToItem(0)
        }
    }
}

/** The time a list is sorted by, so the order explains itself. Null in name order. */
@Composable
fun appSortTimeText(app: InstalledApp, order: AppSortOrder): String? {
    val (millis, label) = when (order) {
        AppSortOrder.NAME -> return null
        AppSortOrder.INSTALL_TIME -> app.firstInstallTimeMillis to R.string.app_list_installed_at
        AppSortOrder.UPDATE_TIME -> app.lastUpdateTimeMillis to R.string.app_list_updated_at
    }
    if (millis == null) return null
    val context = LocalContext.current
    val date = remember(millis) {
        "${DateFormat.getMediumDateFormat(context).format(millis)} ${DateFormat.getTimeFormat(context).format(millis)}"
    }
    return stringResource(label, date)
}

private fun AppSortOrder.labelRes(): Int = when (this) {
    AppSortOrder.NAME -> R.string.app_list_sort_name
    AppSortOrder.INSTALL_TIME -> R.string.app_list_sort_install_time
    AppSortOrder.UPDATE_TIME -> R.string.app_list_sort_update_time
}
