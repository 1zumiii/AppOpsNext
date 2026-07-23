package dev.izumi.appopsnext.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.presentation.app_detail.AppDetailScreen
import dev.izumi.appopsnext.presentation.app_detail.AppDetailUiState
import dev.izumi.appopsnext.presentation.app_detail.AppOpModeChangeUiState
import dev.izumi.appopsnext.presentation.app_list.AppListScreen
import dev.izumi.appopsnext.presentation.app_list.AppListUiState
import dev.izumi.appopsnext.presentation.components.AppNavigationBar
import dev.izumi.appopsnext.presentation.components.MainDestination
import dev.izumi.appopsnext.presentation.home.HomeScreen
import dev.izumi.appopsnext.presentation.home.HomeUiState
import dev.izumi.appopsnext.presentation.settings.SettingsScreen
import dev.izumi.appopsnext.presentation.settings.SettingsUiState

@Composable
fun AppOpsRootScreen(
    homeUiState: HomeUiState,
    appListUiState: AppListUiState,
    appDetailUiState: AppDetailUiState,
    appOpModeChangeUiState: AppOpModeChangeUiState,
    settingsUiState: SettingsUiState,
    appOpSearchQuery: String,
    onShizukuAction: () -> Unit,
    onAppSearchQueryChange: (String) -> Unit,
    onRefreshApps: () -> Unit,
    onAppSelected: (InstalledApp) -> Unit,
    onRefreshAppDetail: () -> Unit,
    onAppOpSearchQueryChange: (String) -> Unit,
    onAppOpModeChangeRequested: (
        String,
        AppOpScope,
        AppOpMode,
        AppOpMode,
    ) -> Unit,
    onAppOpModeChangeConfirmed: () -> Unit,
    onAppOpModeChangeDismissed: () -> Unit,
    onHideSystemAppsChange: (Boolean) -> Unit,
) {
    var selectedDestination by rememberSaveable {
        mutableStateOf(MainDestination.APPS)
    }
    var selectedApp by remember { mutableStateOf<InstalledApp?>(null) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val navigateBackFromDetail = {
        focusManager.clearFocus()
        keyboardController?.hide()
        onAppOpModeChangeDismissed()
        selectedApp = null
    }
    val navigationBar: @Composable () -> Unit = {
        AppNavigationBar(
            selectedDestination = selectedDestination,
            onDestinationSelected = {
                focusManager.clearFocus()
                keyboardController?.hide()
                selectedApp = null
                selectedDestination = it
            },
        )
    }

    BackHandler(enabled = selectedApp != null) {
        navigateBackFromDetail()
    }

    if (selectedApp != null) {
        AppDetailScreen(
            uiState = appDetailUiState,
            modeChangeState = appOpModeChangeUiState,
            searchQuery = appOpSearchQuery,
            onBack = navigateBackFromDetail,
            onRefresh = onRefreshAppDetail,
            onSearchQueryChange = onAppOpSearchQueryChange,
            onModeChangeRequested = onAppOpModeChangeRequested,
            onModeChangeConfirmed = onAppOpModeChangeConfirmed,
            onModeChangeDismissed = onAppOpModeChangeDismissed,
        )
    } else {
        when (selectedDestination) {
            MainDestination.APPS -> AppListScreen(
                uiState = appListUiState,
                onSearchQueryChange = onAppSearchQueryChange,
                onRefresh = onRefreshApps,
                onAppSelected = { app ->
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    selectedApp = app
                    onAppSelected(app)
                },
                bottomBar = navigationBar,
            )

            MainDestination.DIAGNOSTICS -> HomeScreen(
                uiState = homeUiState,
                onShizukuAction = onShizukuAction,
                bottomBar = navigationBar,
            )

            MainDestination.SETTINGS -> SettingsScreen(
                uiState = settingsUiState,
                onHideSystemAppsChange = onHideSystemAppsChange,
                bottomBar = navigationBar,
            )
        }
    }
}
