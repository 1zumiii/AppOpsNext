package dev.izumi.appops

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.izumi.appops.development.DevelopmentWindowPolicy
import dev.izumi.appops.presentation.home.HomeScreen
import dev.izumi.appops.presentation.home.HomeViewModel
import dev.izumi.appops.ui.theme.AppOpsTheme

class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DevelopmentWindowPolicy.apply(window)

        setContent {
            val uiState = viewModel.uiState.collectAsStateWithLifecycle()

            AppOpsTheme {
                HomeScreen(
                    uiState = uiState.value,
                    onShizukuAction = viewModel::performShizukuAction,
                )
            }
        }
    }
}
