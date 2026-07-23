package dev.izumi.appops.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.izumi.appops.presentation.app_list.AppListScreen
import dev.izumi.appops.presentation.app_list.AppListUiState
import dev.izumi.appops.presentation.components.AppNavigationBar
import dev.izumi.appops.presentation.components.MainDestination
import dev.izumi.appops.presentation.home.HomeScreen
import dev.izumi.appops.presentation.home.HomeUiState

@Composable
fun AppOpsRootScreen(
    homeUiState: HomeUiState,
    appListUiState: AppListUiState,
    onShizukuAction: () -> Unit,
    onAppOpsWriteTest: () -> Unit,
    onAppSearchQueryChange: (String) -> Unit,
    onRefreshApps: () -> Unit,
) {
    var selectedDestination by rememberSaveable {
        mutableStateOf(MainDestination.APPS)
    }
    val navigationBar: @Composable () -> Unit = {
        AppNavigationBar(
            selectedDestination = selectedDestination,
            onDestinationSelected = { selectedDestination = it },
        )
    }

    when (selectedDestination) {
        MainDestination.APPS -> AppListScreen(
            uiState = appListUiState,
            onSearchQueryChange = onAppSearchQueryChange,
            onRefresh = onRefreshApps,
            bottomBar = navigationBar,
        )

        MainDestination.DIAGNOSTICS -> HomeScreen(
            uiState = homeUiState,
            onShizukuAction = onShizukuAction,
            onAppOpsWriteTest = onAppOpsWriteTest,
            bottomBar = navigationBar,
        )
    }
}
