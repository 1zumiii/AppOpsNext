package dev.izumi.appopsnext

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.izumi.appopsnext.development.DevelopmentWindowPolicy
import dev.izumi.appopsnext.presentation.AppOpsRootScreen
import dev.izumi.appopsnext.presentation.app_detail.AppDetailViewModel
import dev.izumi.appopsnext.presentation.app_list.AppListViewModel
import dev.izumi.appopsnext.presentation.home.HomeViewModel
import dev.izumi.appopsnext.presentation.settings.SettingsViewModel
import dev.izumi.appopsnext.ui.theme.AppOpsNextTheme

class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModels()
    private val appListViewModel: AppListViewModel by viewModels()
    private val appDetailViewModel: AppDetailViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DevelopmentWindowPolicy.apply(window)

        setContent {
            val homeUiState = homeViewModel.uiState.collectAsStateWithLifecycle()
            val appListUiState =
                appListViewModel.uiState.collectAsStateWithLifecycle()
            val appDetailUiState =
                appDetailViewModel.uiState.collectAsStateWithLifecycle()
            val appOpModeChangeUiState =
                appDetailViewModel.modeChangeState.collectAsStateWithLifecycle()
            val settingsUiState =
                settingsViewModel.uiState.collectAsStateWithLifecycle()
            val appOpSearchQuery =
                appDetailViewModel.searchQuery.collectAsStateWithLifecycle()

            AppOpsNextTheme {
                AppOpsRootScreen(
                    homeUiState = homeUiState.value,
                    appListUiState = appListUiState.value,
                    appDetailUiState = appDetailUiState.value,
                    appOpModeChangeUiState = appOpModeChangeUiState.value,
                    settingsUiState = settingsUiState.value,
                    appOpSearchQuery = appOpSearchQuery.value,
                    onShizukuAction = homeViewModel::performShizukuAction,
                    onAppSearchQueryChange = appListViewModel::updateSearchQuery,
                    onRefreshApps = appListViewModel::refresh,
                    onAppSelected = appDetailViewModel::selectApp,
                    onRefreshAppDetail = appDetailViewModel::refresh,
                    onAppOpSearchQueryChange =
                        appDetailViewModel::updateSearchQuery,
                    onAppOpModeChangeRequested =
                        appDetailViewModel::requestModeChange,
                    onAppOpModeChangeConfirmed =
                        appDetailViewModel::confirmModeChange,
                    onAppOpModeChangeDismissed =
                        appDetailViewModel::dismissModeChange,
                    onHideSystemAppsChange =
                        settingsViewModel::setHideSystemApps,
                )
            }
        }
    }
}
