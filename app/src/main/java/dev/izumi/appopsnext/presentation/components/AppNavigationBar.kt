package dev.izumi.appopsnext.presentation.components

import androidx.annotation.StringRes
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.izumi.appopsnext.R

enum class MainDestination(
    @StringRes val labelRes: Int,
    @StringRes val shortLabelRes: Int,
) {
    APPS(
        labelRes = R.string.navigation_apps,
        shortLabelRes = R.string.navigation_apps_short,
    ),
    DIAGNOSTICS(
        labelRes = R.string.navigation_diagnostics,
        shortLabelRes = R.string.navigation_diagnostics_short,
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
                    Text(text = stringResource(destination.shortLabelRes))
                },
                label = {
                    Text(text = stringResource(destination.labelRes))
                },
            )
        }
    }
}
