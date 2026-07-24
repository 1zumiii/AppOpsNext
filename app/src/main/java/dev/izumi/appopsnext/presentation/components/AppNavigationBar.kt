package dev.izumi.appopsnext.presentation.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.izumi.appopsnext.R

enum class MainDestination(
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
) {
    APPS(
        labelRes = R.string.navigation_apps,
        iconRes = R.drawable.ic_navigation_apps,
    ),
    TEMPLATES(
        labelRes = R.string.navigation_templates,
        iconRes = R.drawable.ic_navigation_templates,
    ),
    HISTORY(
        labelRes = R.string.navigation_history,
        iconRes = R.drawable.ic_navigation_history,
    ),
    SETTINGS(
        labelRes = R.string.navigation_settings,
        iconRes = R.drawable.ic_navigation_settings,
    ),
}

@Composable
fun AppNavigationBar(
    selectedDestination: MainDestination,
    onDestinationSelected: (MainDestination) -> Unit,
) {
    NavigationBar {
        MainDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == selectedDestination,
                onClick = { onDestinationSelected(destination) },
                icon = {
                    Icon(
                        painter = painterResource(destination.iconRes),
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
