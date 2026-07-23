package dev.izumi.appops

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.izumi.appops.development.DevelopmentWindowPolicy
import dev.izumi.appops.presentation.AppOpsRootScreen
import dev.izumi.appops.presentation.app_detail.AppDetailViewModel
import dev.izumi.appops.presentation.app_list.AppListViewModel
import dev.izumi.appops.presentation.home.HomeViewModel
import dev.izumi.appops.ui.theme.AppOpsTheme

class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModels()
    private val appListViewModel: AppListViewModel by viewModels()
    private val appDetailViewModel: AppDetailViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DevelopmentWindowPolicy.apply(window)

        setContent {
            val homeUiState = homeViewModel.uiState.collectAsStateWithLifecycle()
            val appListUiState =
                appListViewModel.uiState.collectAsStateWithLifecycle()
            val appDetailUiState =
                appDetailViewModel.uiState.collectAsStateWithLifecycle()

            AppOpsTheme {
                AppOpsRootScreen(
                    homeUiState = homeUiState.value,
                    appListUiState = appListUiState.value,
                    appDetailUiState = appDetailUiState.value,
                    onShizukuAction = homeViewModel::performShizukuAction,
                    onAppOpsWriteTest = homeViewModel::performAppOpsWriteTest,
                    onAppSearchQueryChange = appListViewModel::updateSearchQuery,
                    onRefreshApps = appListViewModel::refresh,
                    onAppSelected = appDetailViewModel::selectApp,
                    onRefreshAppDetail = appDetailViewModel::refresh,
                )
            }
        }
    }
}
