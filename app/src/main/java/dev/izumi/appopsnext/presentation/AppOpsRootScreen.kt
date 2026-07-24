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
import dev.izumi.appopsnext.presentation.batch.BatchOperationDialog
import dev.izumi.appopsnext.presentation.batch.BatchOperationUiState
import dev.izumi.appopsnext.presentation.batch.PermissionBatchSelection
import dev.izumi.appopsnext.presentation.components.AppNavigationBar
import dev.izumi.appopsnext.presentation.components.MainDestination
import dev.izumi.appopsnext.presentation.diagnostics.DiagnosticsUiState
import dev.izumi.appopsnext.presentation.history.HistoryOverviewScreen
import dev.izumi.appopsnext.presentation.history.HistoryUiState
import dev.izumi.appopsnext.presentation.history.PermissionHistoryDetailScreen
import dev.izumi.appopsnext.history.model.HistoryPermission
import dev.izumi.appopsnext.presentation.settings.SettingsScreen
import dev.izumi.appopsnext.presentation.settings.SettingsUiState
import dev.izumi.appopsnext.settings.AppLanguage
import dev.izumi.appopsnext.presentation.templates.TemplatesScreen
import dev.izumi.appopsnext.presentation.templates.TemplatesUiState
import dev.izumi.appopsnext.templates.model.PermissionTemplate

@Composable
fun AppOpsRootScreen(
    diagnosticsUiState: DiagnosticsUiState,
    historyUiState: HistoryUiState,
    appListUiState: AppListUiState,
    appDetailUiState: AppDetailUiState,
    appOpModeChangeUiState: AppOpModeChangeUiState,
    settingsUiState: SettingsUiState,
    templatesUiState: TemplatesUiState,
    batchOperationUiState: BatchOperationUiState,
    appOpSearchQuery: String,
    onShizukuAction: () -> Unit,
    onAppSearchQueryChange: (String) -> Unit,
    onRefreshApps: () -> Unit,
    onRefreshHistory: () -> Unit,
    onHistoryPermissionsChanged: (List<String>) -> Unit,
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
    onAppLanguageChange: (AppLanguage) -> Unit,
    onCreateTemplate: (String) -> Unit,
    onSelectTemplate: (String) -> Unit,
    onCloseTemplateEditor: () -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onTemplateRuleModeChange: (String, AppOpMode) -> Unit,
    onTemplateRuleScopeChange: (String, AppOpScope) -> Unit,
    onAddTemplateRule: (String) -> Unit,
    onRemoveTemplateRule: (String) -> Unit,
    onTemplateRuleOrderChange: (List<String>) -> Unit,
    onTemplateApplyRequested:
        (PermissionTemplate, List<InstalledApp>) -> Unit,
    onPermissionBatchRequested: (
        InstalledApp,
        List<PermissionBatchSelection>,
        AppOpMode,
    ) -> Unit,
    onBatchOperationConfirm: () -> Unit,
    onBatchOperationDismiss: () -> Unit,
) {
    var selectedDestination by rememberSaveable {
        mutableStateOf(MainDestination.APPS)
    }
    var selectedApp by remember { mutableStateOf<InstalledApp?>(null) }
    var selectedHistoryPermissionName by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    val selectedHistoryPermission = selectedHistoryPermissionName?.let {
        HistoryPermission(it)
    }
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
                selectedHistoryPermissionName = null
                selectedDestination = it
            },
        )
    }

    BackHandler(enabled = selectedApp != null) {
        navigateBackFromDetail()
    }
    BackHandler(
        enabled = selectedApp == null && selectedHistoryPermission != null,
    ) {
        selectedHistoryPermissionName = null
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
            templates = templatesUiState.templates,
            onTemplateApplyRequested = { template, app ->
                onTemplateApplyRequested(template, listOf(app))
            },
            onPermissionBatchRequested = onPermissionBatchRequested,
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
                templates = templatesUiState.templates,
                onTemplateApplyRequested = onTemplateApplyRequested,
                bottomBar = navigationBar,
            )

            MainDestination.TEMPLATES -> TemplatesScreen(
                uiState = templatesUiState,
                onCreateTemplate = onCreateTemplate,
                onSelectTemplate = onSelectTemplate,
                onCloseEditor = onCloseTemplateEditor,
                onDeleteTemplate = onDeleteTemplate,
                onRuleModeChange = onTemplateRuleModeChange,
                onRuleScopeChange = onTemplateRuleScopeChange,
                onAddRule = onAddTemplateRule,
                onRemoveRule = onRemoveTemplateRule,
                onRuleOrderChange = onTemplateRuleOrderChange,
                bottomBar = navigationBar,
            )

            MainDestination.HISTORY -> {
                if (selectedHistoryPermission == null) {
                    HistoryOverviewScreen(
                        uiState = historyUiState,
                        onRefresh = onRefreshHistory,
                        onPermissionSelected = { permission ->
                            selectedHistoryPermissionName =
                                permission.shellOperationName
                        },
                        onPermissionsChanged = onHistoryPermissionsChanged,
                        bottomBar = navigationBar,
                    )
                } else {
                    PermissionHistoryDetailScreen(
                        permission = selectedHistoryPermission,
                        history = historyUiState.permissions.firstOrNull {
                            it.permission == selectedHistoryPermission
                        },
                        isLoading = historyUiState.isLoading,
                        onBack = {
                            selectedHistoryPermissionName = null
                        },
                        onAppSelected = { app ->
                            selectedApp = app
                            onAppSelected(app)
                        },
                    )
                }
            }

            MainDestination.SETTINGS -> SettingsScreen(
                uiState = settingsUiState,
                diagnosticsUiState = diagnosticsUiState,
                onHideSystemAppsChange = onHideSystemAppsChange,
                onAppLanguageChange = onAppLanguageChange,
                onShizukuAction = onShizukuAction,
                bottomBar = navigationBar,
            )
        }
    }

    BatchOperationDialog(
        state = batchOperationUiState,
        onConfirm = onBatchOperationConfirm,
        onDismiss = onBatchOperationDismiss,
    )
}
