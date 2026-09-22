package dev.izumi.appopsnext

import android.os.Bundle
import android.content.Intent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import dev.izumi.appopsnext.monitor.AppOpsMonitorService
import dev.izumi.appopsnext.newapps.NewAppPolicyNotifier
import kotlinx.coroutines.flow.first
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
import dev.izumi.appopsnext.presentation.experimental.ExperimentalViewModel
import dev.izumi.appopsnext.presentation.experimental.WatchersViewModel
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
    private val experimentalViewModel: ExperimentalViewModel by viewModels()
    private val watchersViewModel: WatchersViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DevelopmentWindowPolicy.apply(window)
        restoreBackgroundMonitor()
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
            val experimentalUiState =
                experimentalViewModel.uiState.collectAsStateWithLifecycle()
            val watchersUiState = watchersViewModel.uiState.collectAsStateWithLifecycle()

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
                    experimentalUiState = experimentalUiState.value,
                    watchersUiState = watchersUiState.value,
                    onRefreshWatchers = watchersViewModel::refresh,
                    onMonitorEnabledChange =
                        experimentalViewModel::setMonitorEnabled,
                    onEnableUnconfirmedMonitor =
                        experimentalViewModel::enableUnconfirmedMonitor,
                    onMonitorHeadsUpChange =
                        experimentalViewModel::setHeadsUp,
                    onCheckForUpdate =
                        settingsViewModel::checkForUpdate,
                    onOpenBatterySettings = ::openBatterySettings,
                    onDismissBatteryNotice =
                        experimentalViewModel::dismissBatteryNotice,
                    onRefreshBatteryExemption =
                        experimentalViewModel::refreshBatteryExemption,
                    onMonitorOperationsChange =
                        experimentalViewModel::setOperations,
                    onMonitorThrottleChange =
                        experimentalViewModel::setThrottle,
                    onMonitorPointHeadsUpChange =
                        experimentalViewModel::setPointHeadsUp,
                    onMonitorPointOutcomesChange =
                        experimentalViewModel::setPointOutcomes,
                    onMonitorPointBackgroundOnlyChange =
                        experimentalViewModel::setPointBackgroundOnly,
                    onMonitorShowAllOperationsChange =
                        experimentalViewModel::setShowAllOperations,
                    onMonitorSuppressAllOperationsWarning =
                        experimentalViewModel::suppressAllOperationsWarning,
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
                    onSaveIndividualHistoryChange =
                        settingsViewModel::setSaveIndividualHistory,
                    countSavedHistory =
                        settingsViewModel::countSavedHistory,
                    onDeleteSavedHistory =
                        settingsViewModel::deleteSavedHistory,
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

    /**
     * A force stop tears the service down and stops the system from restarting
     * it, so the switch would read as on while nothing was being watched. This
     * brings the service back the next time the user opens the app.
     */
    private fun restoreBackgroundMonitor() {
        val application = application as AppOpsNextApplication
        lifecycleScope.launch {
            val enabled = runCatching {
                application.userSettingsRepository.settings.first().backgroundMonitor
            }.getOrDefault(false)
            if (enabled && !application.appOpsMonitorController.isRunning) {
                runCatching {
                    AppOpsMonitorService.start(this@MainActivity)
                }.onFailure { error ->
                    application.diagnosticLogRepository.error(
                        source = "Monitor",
                        message = "Unable to restore the background monitor.",
                        error = error,
                    )
                }
            }
        }
    }

    /**
     * Opens the system's battery-optimisation list rather than requesting the
     * exemption directly: the direct request needs a permission that app stores
     * restrict, and this route works from any app.
     */
    private fun openBatterySettings() {
        runCatching {
            startActivity(
                Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )
        }
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
