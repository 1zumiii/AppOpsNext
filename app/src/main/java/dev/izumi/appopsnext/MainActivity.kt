package dev.izumi.appopsnext

import android.os.Bundle
import android.content.Intent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import dev.izumi.appopsnext.newapps.NewAppPolicyNotifier
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
        showNewAppResult(intent)

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
                    onPrivilegedServiceRetry =
                        diagnosticsViewModel::retryPrivilegedService,
                    onClearDiagnosticLog =
                        diagnosticsViewModel::clearDiagnosticLog,
                    onAppSearchQueryChange = appListViewModel::updateSearchQuery,
                    onRefreshApps = appListViewModel::refresh,
                    onRefreshHistory = historyViewModel::refresh,
                    onHistoryVisibilityChanged = historyViewModel::setVisible,
                    onHistoryPermissionsChanged =
                        historyViewModel::setPermissions,
                    onHistoryPermissionOrderChanged =
                        historyViewModel::setPermissionOrder,
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
                    onDenyFallbackNoticeDismissed =
                        appDetailViewModel::dismissDenyFallbackNotice,
                    onForegroundAlternativeRequested =
                        appDetailViewModel::requestForegroundAlternative,
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
                    onTemplateRuleSelectionChange =
                        templatesViewModel::setRuleSelection,
                    onTemplateRuleOrderChange =
                        templatesViewModel::setRuleOrder,
                    onAutoApplyNewAppTemplateChange =
                        templatesViewModel::setAutoApplyNewAppTemplate,
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showNewAppResult(intent)
    }

    private fun showNewAppResult(intent: Intent) {
        val packageName = intent.getStringExtra(NewAppPolicyNotifier.EXTRA_PACKAGE) ?: return
        val installedAt = intent.getLongExtra(NewAppPolicyNotifier.EXTRA_INSTALL_TIME, -1)
        intent.removeExtra(NewAppPolicyNotifier.EXTRA_PACKAGE)
        lifecycleScope.launch {
            (application as AppOpsNextApplication).newAppPolicyCoordinator
                .reportFor(packageName, installedAt)?.let(batchOperationsViewModel::showReport)
        }
    }

    override fun onPause() {
        historyViewModel.setForeground(false)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        (application as AppOpsNextApplication)
            .newAppPolicyCoordinator
            .onAppForeground()
        appListViewModel.refreshAfterResume()
        appDetailViewModel.refreshIfReady()
        historyViewModel.setForeground(true)
    }
}
