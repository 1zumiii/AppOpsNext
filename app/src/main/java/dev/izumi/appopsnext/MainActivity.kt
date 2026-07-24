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
import dev.izumi.appopsnext.presentation.batch.BatchOperationsViewModel
import dev.izumi.appopsnext.presentation.diagnostics.DiagnosticsViewModel
import dev.izumi.appopsnext.presentation.history.HistoryViewModel
import dev.izumi.appopsnext.presentation.settings.SettingsViewModel
import dev.izumi.appopsnext.presentation.templates.TemplatesViewModel
import dev.izumi.appopsnext.ui.theme.AppOpsNextTheme

class MainActivity : ComponentActivity() {
    private val diagnosticsViewModel: DiagnosticsViewModel by viewModels()
    private val appListViewModel: AppListViewModel by viewModels()
    private val historyViewModel: HistoryViewModel by viewModels()
    private val appDetailViewModel: AppDetailViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val templatesViewModel: TemplatesViewModel by viewModels()
    private val batchOperationsViewModel: BatchOperationsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DevelopmentWindowPolicy.apply(window)

        setContent {
            val diagnosticsUiState =
                diagnosticsViewModel.uiState.collectAsStateWithLifecycle()
            val appListUiState =
                appListViewModel.uiState.collectAsStateWithLifecycle()
            val historyUiState =
                historyViewModel.uiState.collectAsStateWithLifecycle()
            val appDetailUiState =
                appDetailViewModel.uiState.collectAsStateWithLifecycle()
            val appOpModeChangeUiState =
                appDetailViewModel.modeChangeState.collectAsStateWithLifecycle()
            val settingsUiState =
                settingsViewModel.uiState.collectAsStateWithLifecycle()
            val templatesUiState =
                templatesViewModel.uiState.collectAsStateWithLifecycle()
            val batchOperationUiState =
                batchOperationsViewModel.uiState.collectAsStateWithLifecycle()
            val appOpSearchQuery =
                appDetailViewModel.searchQuery.collectAsStateWithLifecycle()

            AppOpsNextTheme {
                AppOpsRootScreen(
                    diagnosticsUiState = diagnosticsUiState.value,
                    historyUiState = historyUiState.value,
                    appListUiState = appListUiState.value,
                    appDetailUiState = appDetailUiState.value,
                    appOpModeChangeUiState = appOpModeChangeUiState.value,
                    settingsUiState = settingsUiState.value,
                    templatesUiState = templatesUiState.value,
                    batchOperationUiState = batchOperationUiState.value,
                    appOpSearchQuery = appOpSearchQuery.value,
                    onShizukuAction =
                        diagnosticsViewModel::performShizukuAction,
                    onAppSearchQueryChange = appListViewModel::updateSearchQuery,
                    onRefreshApps = appListViewModel::refresh,
                    onRefreshHistory = historyViewModel::refresh,
                    onHistoryPermissionsChanged =
                        historyViewModel::setPermissions,
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
                    onAppLanguageChange =
                        settingsViewModel::setAppLanguage,
                    onCreateTemplate = templatesViewModel::createTemplate,
                    onSelectTemplate = templatesViewModel::selectTemplate,
                    onCloseTemplateEditor = templatesViewModel::closeEditor,
                    onDeleteTemplate = templatesViewModel::deleteTemplate,
                    onTemplateRuleModeChange =
                        templatesViewModel::setRuleMode,
                    onTemplateRuleScopeChange =
                        templatesViewModel::setRuleScope,
                    onAddTemplateRule = templatesViewModel::addRule,
                    onRemoveTemplateRule = templatesViewModel::removeRule,
                    onTemplateRuleOrderChange =
                        templatesViewModel::setRuleOrder,
                    onTemplateApplyRequested =
                        batchOperationsViewModel::requestTemplateApplication,
                    onPermissionBatchRequested =
                        batchOperationsViewModel::requestPermissionBatch,
                    onBatchOperationConfirm =
                        batchOperationsViewModel::confirm,
                    onBatchOperationDismiss = {
                        batchOperationsViewModel.dismiss()
                        appDetailViewModel.refresh()
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        historyViewModel.refresh()
    }
}
