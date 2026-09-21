package dev.izumi.appopsnext.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
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
import dev.izumi.appopsnext.presentation.experimental.ExperimentalScreen
import dev.izumi.appopsnext.presentation.experimental.ExperimentalUiState
import dev.izumi.appopsnext.presentation.experimental.MonitorOperationsScreen
import dev.izumi.appopsnext.presentation.experimental.MonitorSettingsScreen
import dev.izumi.appopsnext.presentation.experimental.MonitorTargetsScreen
import dev.izumi.appopsnext.presentation.history.HistoryOverviewScreen
import dev.izumi.appopsnext.presentation.history.HistoryAppStatisticsScreen
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
    onPrivilegedServiceRetry: () -> Unit,
    onClearDiagnosticLog: () -> Unit,
    onAppSearchQueryChange: (String) -> Unit,
    onRefreshApps: () -> Unit,
    onRefreshHistory: () -> Unit,
    onHistoryVisibilityChanged: (Boolean) -> Unit,
    onHistoryPermissionsChanged: (List<String>) -> Unit,
    onHistoryPermissionOrderChanged: (List<String>) -> Unit,
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
    onDenyFallbackNoticeDismissed: (Boolean) -> Unit,
    onForegroundAlternativeRequested: () -> Unit,
    onHideSystemAppsChange: (Boolean) -> Unit,
    experimentalUiState: ExperimentalUiState,
    onMonitorEnabledChange: (Boolean) -> Unit,
    onMonitorHeadsUpChange: (Boolean) -> Unit,
    onCheckForUpdate: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onDismissBatteryNotice: () -> Unit,
    onRefreshBatteryExemption: () -> Unit,
    onMonitorOperationsChange: (String, Set<String>) -> Unit,
    onAppLanguageChange: (AppLanguage) -> Unit,
    onCreateTemplate: (String) -> Unit,
    onSelectTemplate: (String) -> Unit,
    onCloseTemplateEditor: () -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onTemplateRuleModeChange: (String, AppOpMode) -> Unit,
    onTemplateRuleSelectionChange: (List<String>) -> Unit,
    onTemplateRuleOrderChange: (List<String>) -> Unit,
    onAutoApplyNewAppTemplateChange: (Boolean) -> Unit,
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
    var selectedApp by rememberSaveable(stateSaver = InstalledAppStateSaver) {
        mutableStateOf<InstalledApp?>(null)
    }
    var selectedHistoryPermissionName by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var showHistoryAppStatistics by rememberSaveable {
        mutableStateOf(false)
    }
    var experimentalRoute by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var monitorAppPackage by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    val selectedHistoryPermission = selectedHistoryPermissionName?.let {
        HistoryPermission(it)
    }
    // The detail view model starts empty after the process is recreated, so a
    // restored selection is re-applied once. Later selections notify it directly.
    LaunchedEffect(Unit) {
        selectedApp?.let(onAppSelected)
    }
    val historyVisible = selectedDestination == MainDestination.HISTORY && selectedApp == null
    DisposableEffect(historyVisible) {
        onHistoryVisibilityChanged(historyVisible)
        onDispose { onHistoryVisibilityChanged(false) }
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
                showHistoryAppStatistics = false
                selectedDestination = it
            },
        )
    }

    BackHandler(enabled = selectedApp == null && monitorAppPackage != null) {
        monitorAppPackage = null
    }
    BackHandler(
        enabled = selectedApp == null &&
            monitorAppPackage == null &&
            experimentalRoute == ROUTE_MONITOR_TARGETS,
    ) {
        experimentalRoute = ROUTE_MONITOR_SETTINGS
    }
    BackHandler(
        enabled = selectedApp == null &&
            monitorAppPackage == null &&
            experimentalRoute == ROUTE_MONITOR_SETTINGS,
    ) {
        experimentalRoute = ROUTE_EXPERIMENTAL
    }
    BackHandler(
        enabled = selectedApp == null &&
            monitorAppPackage == null &&
            experimentalRoute == ROUTE_EXPERIMENTAL,
    ) {
        experimentalRoute = null
    }

    BackHandler(enabled = selectedApp != null) {
        navigateBackFromDetail()
    }
    BackHandler(
        enabled = selectedApp == null &&
            selectedHistoryPermission != null &&
            showHistoryAppStatistics,
    ) {
        showHistoryAppStatistics = false
    }
    BackHandler(
        enabled = selectedApp == null &&
            selectedHistoryPermission != null &&
            !showHistoryAppStatistics,
    ) {
        selectedHistoryPermissionName = null
    }

    val monitorApp = monitorAppPackage?.let { packageName ->
        appListUiState.allApps.firstOrNull { it.packageName == packageName }
    }
    if (selectedApp == null && experimentalRoute != null) {
        when {
            monitorApp != null -> MonitorOperationsScreen(
                app = monitorApp,
                selected = experimentalUiState
                    .selectionByPackage[monitorApp.packageName]
                    .orEmpty(),
                onBack = { monitorAppPackage = null },
                onSelectionChange = { operations ->
                    onMonitorOperationsChange(monitorApp.packageName, operations)
                },
            )

            experimentalRoute == ROUTE_MONITOR_TARGETS -> MonitorTargetsScreen(
                apps = appListUiState.allApps,
                selectionByPackage = experimentalUiState.selectionByPackage,
                onBack = { experimentalRoute = ROUTE_MONITOR_SETTINGS },
                onAppSelected = { app -> monitorAppPackage = app.packageName },
            )

            experimentalRoute == ROUTE_MONITOR_SETTINGS -> MonitorSettingsScreen(
                uiState = experimentalUiState,
                onBack = { experimentalRoute = ROUTE_EXPERIMENTAL },
                onOpenTargets = { experimentalRoute = ROUTE_MONITOR_TARGETS },
                onHeadsUpChange = onMonitorHeadsUpChange,
            )

            else -> ExperimentalScreen(
                uiState = experimentalUiState,
                onBack = { experimentalRoute = null },
                onMonitorChange = onMonitorEnabledChange,
                onOpenSettings = { experimentalRoute = ROUTE_MONITOR_SETTINGS },
                onOpenBatterySettings = onOpenBatterySettings,
                onDismissBatteryNotice = onDismissBatteryNotice,
                onRefreshBatteryExemption = onRefreshBatteryExemption,
            )
        }
        return
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
            onDenyFallbackNoticeDismissed =
                onDenyFallbackNoticeDismissed,
            onForegroundAlternativeRequested =
                onForegroundAlternativeRequested,
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
                onRuleSelectionChange = onTemplateRuleSelectionChange,
                onRuleOrderChange = onTemplateRuleOrderChange,
                onAutoApplyNewAppTemplateChange =
                    onAutoApplyNewAppTemplateChange,
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
                            showHistoryAppStatistics = false
                        },
                        onPermissionsChanged = onHistoryPermissionsChanged,
                        onPermissionOrderChanged =
                            onHistoryPermissionOrderChanged,
                        bottomBar = navigationBar,
                    )
                } else if (showHistoryAppStatistics) {
                    HistoryAppStatisticsScreen(
                        history = historyUiState.permissions.firstOrNull {
                            it.permission == selectedHistoryPermission
                        },
                        onBack = {
                            showHistoryAppStatistics = false
                        },
                        onAppSelected = { app ->
                            selectedApp = app
                            onAppSelected(app)
                        },
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
                        onAppsSelected = {
                            showHistoryAppStatistics = true
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
                onOpenExperimental = { experimentalRoute = ROUTE_EXPERIMENTAL },
                onCheckForUpdate = onCheckForUpdate,
                onAppLanguageChange = onAppLanguageChange,
                onShizukuAction = onShizukuAction,
                onPrivilegedServiceRetry = onPrivilegedServiceRetry,
                onClearDiagnosticLog = onClearDiagnosticLog,
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

/** Keeps the opened app across activity recreation, such as a rotation. */
private val InstalledAppStateSaver = listSaver<InstalledApp?, Any>(
    save = { app ->
        app?.let {
            listOf(it.label, it.packageName, it.uid, it.isSystemApp)
        }.orEmpty()
    },
    restore = { values ->
        values.takeIf { it.size == SAVED_APP_FIELD_COUNT }?.let {
            InstalledApp(
                label = it[0] as String,
                packageName = it[1] as String,
                uid = it[2] as Int,
                isSystemApp = it[3] as Boolean,
            )
        }
    },
)

private const val SAVED_APP_FIELD_COUNT = 4
private const val ROUTE_EXPERIMENTAL = "experimental"
private const val ROUTE_MONITOR_SETTINGS = "monitor_settings"
private const val ROUTE_MONITOR_TARGETS = "monitor_targets"
