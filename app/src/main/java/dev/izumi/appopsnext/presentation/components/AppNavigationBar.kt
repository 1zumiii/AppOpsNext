package dev.izumi.appopsnext.presentation.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R

enum class MainDestination(
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    @DrawableRes val selectedIconRes: Int,
) {
    APPS(
        labelRes = R.string.navigation_apps,
        iconRes = R.drawable.ic_ph_squares_four,
        selectedIconRes = R.drawable.ic_ph_squares_four_fill,
    ),
    TEMPLATES(
        labelRes = R.string.navigation_templates,
        iconRes = R.drawable.ic_ph_file_text,
        selectedIconRes = R.drawable.ic_ph_file_text_fill,
    ),
    HISTORY(
        labelRes = R.string.navigation_history,
        iconRes = R.drawable.ic_ph_clock_counter_clockwise,
        selectedIconRes = R.drawable.ic_ph_clock_counter_clockwise_fill,
    ),
    SETTINGS(
        labelRes = R.string.navigation_settings,
        iconRes = R.drawable.ic_ph_gear,
        selectedIconRes = R.drawable.ic_ph_gear_fill,
    ),
}

@Composable
fun AppNavigationBar(
    selectedDestination: MainDestination,
    onDestinationSelected: (MainDestination) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        MainDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == selectedDestination,
                onClick = { onDestinationSelected(destination) },
                icon = {
                    Icon(
                        painter = painterResource(
                            if (destination == selectedDestination) {
                                destination.selectedIconRes
                            } else {
                                destination.iconRes
                            },
                        ),
                        contentDescription = stringResource(
                            destination.labelRes,
                        ),
                    )
                },
                label = {
                    Text(text = stringResource(destination.labelRes))
                },
            )
        }
    }
}
