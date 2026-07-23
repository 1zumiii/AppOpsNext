package dev.izumi.appops.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.izumi.appops.apps.model.InstalledApp
import dev.izumi.appops.presentation.app_detail.AppDetailScreen
import dev.izumi.appops.presentation.app_detail.AppDetailUiState
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
    appDetailUiState: AppDetailUiState,
    onShizukuAction: () -> Unit,
    onAppOpsWriteTest: () -> Unit,
    onAppSearchQueryChange: (String) -> Unit,
    onRefreshApps: () -> Unit,
    onAppSelected: (InstalledApp) -> Unit,
    onRefreshAppDetail: () -> Unit,
) {
    var selectedDestination by rememberSaveable {
        mutableStateOf(MainDestination.APPS)
    }
    var selectedApp by remember { mutableStateOf<InstalledApp?>(null) }
    val navigationBar: @Composable () -> Unit = {
        AppNavigationBar(
            selectedDestination = selectedDestination,
            onDestinationSelected = {
                selectedApp = null
                selectedDestination = it
            },
        )
    }

    if (selectedApp != null) {
        AppDetailScreen(
            uiState = appDetailUiState,
            onBack = { selectedApp = null },
            onRefresh = onRefreshAppDetail,
        )
    } else {
        when (selectedDestination) {
            MainDestination.APPS -> AppListScreen(
                uiState = appListUiState,
                onSearchQueryChange = onAppSearchQueryChange,
                onRefresh = onRefreshApps,
                onAppSelected = { app ->
                    selectedApp = app
                    onAppSelected(app)
                },
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
}
