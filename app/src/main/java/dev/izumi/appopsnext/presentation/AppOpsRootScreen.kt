package dev.izumi.appopsnext.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
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
import dev.izumi.appopsnext.monitor.MonitorOutcomes
import dev.izumi.appopsnext.presentation.app_detail.AppOpDisplayCatalog
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
import dev.izumi.appopsnext.presentation.experimental.WatchersScreen
import dev.izumi.appopsnext.presentation.experimental.WatchersUiState
import dev.izumi.appopsnext.presentation.experimental.ExperimentalUiState
import dev.izumi.appopsnext.presentation.experimental.MonitorOperationsScreen
import dev.izumi.appopsnext.presentation.experimental.MonitorSettingsScreen
import dev.izumi.appopsnext.presentation.experimental.MonitorTargetsScreen
import dev.izumi.appopsnext.presentation.experimental.MonitorExamplesScreen
import dev.izumi.appopsnext.presentation.experimental.MonitorPointDetailScreen
import dev.izumi.appopsnext.presentation.experimental.MonitorPointRow
import dev.izumi.appopsnext.presentation.experimental.MonitorPointsScreen
import dev.izumi.appopsnext.presentation.history.HistoryOverviewScreen
import dev.izumi.appopsnext.presentation.history.HistoryAppStatisticsScreen
import dev.izumi.appopsnext.presentation.history.HistoryFilter
import dev.izumi.appopsnext.presentation.settings.SavedHistoryDateRange
import dev.izumi.appopsnext.presentation.settings.SavedHistoryScreen
import dev.izumi.appopsnext.presentation.history.HistoryTimeRange
import dev.izumi.appopsnext.presentation.history.HistoryUiState
import dev.izumi.appopsnext.presentation.history.PermissionHistoryDetailScreen
import dev.izumi.appopsnext.history.model.HistoryPermission
import dev.izumi.appopsnext.presentation.settings.SettingsScreen
import dev.izumi.appopsnext.presentation.settings.SettingsUiState
import dev.izumi.appopsnext.settings.AppLanguage
import dev.izumi.appopsnext.presentation.templates.TemplatesScreen
import dev.izumi.appopsnext.presentation.templates.TemplatesUiState
import dev.izumi.appopsnext.templates.model.PermissionTemplate
import java.time.ZoneId

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
    onSaveIndividualHistoryChange: (Boolean) -> Unit,
    countSavedHistory: (SavedHistoryDateRange?) -> Int,
    onDeleteSavedHistory: (SavedHistoryDateRange?) -> Unit,
    experimentalUiState: ExperimentalUiState,
    watchersUiState: WatchersUiState,
    onRefreshWatchers: () -> Unit,
    onMonitorEnabledChange: (Boolean) -> Unit,
    onEnableUnconfirmedMonitor: () -> Unit,
    onMonitorHeadsUpChange: (Boolean) -> Unit,
    onCheckForUpdate: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onDismissBatteryNotice: () -> Unit,
    onRefreshBatteryExemption: () -> Unit,
    onMonitorOperationsChange: (String, Set<String>) -> Unit,
    onMonitorThrottleChange: (String, String, Int?) -> Unit,
    onMonitorPointHeadsUpChange: (String, String, Boolean?) -> Unit,
    onMonitorPointOutcomesChange: (String, String, MonitorOutcomes) -> Unit,
    onMonitorPointBackgroundOnlyChange: (String, String, Boolean) -> Unit,
    onMonitorShowAllOperationsChange: (Boolean) -> Unit,
    onMonitorSuppressAllOperationsWarning: () -> Unit,
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
    // The range is shared across permissions; the app filter belongs to one.
    var historyTimeRange by rememberSaveable {
        mutableStateOf(HistoryTimeRange.WEEK)
    }
    var historyAppFilter by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var showSavedHistory by rememberSaveable { mutableStateOf(false) }
    var experimentalRoute by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var monitorPointKey by rememberSaveable { mutableStateOf<String?>(null) }
    var monitorAppPackage by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    val selectedHistoryPermission = selectedHistoryPermissionName?.let {
        HistoryPermission(it)
    }
    // "All" disappears when saving is switched off, leaving the longest real range.
    val effectiveHistoryRange = historyTimeRange.takeIf {
        it in HistoryTimeRange.available(historyUiState.saveIndividualHistory)
    } ?: HistoryTimeRange.MONTH
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

    BackHandler(enabled = selectedApp == null && monitorPointKey != null) {
        monitorPointKey = null
    }
    BackHandler(
        enabled = selectedApp == null && monitorPointKey == null && monitorAppPackage != null,
    ) {
        monitorAppPackage = null
    }
    BackHandler(
        enabled = selectedApp == null &&
            monitorPointKey == null &&
            monitorAppPackage == null &&
            (
                experimentalRoute == ROUTE_MONITOR_TARGETS ||
                    experimentalRoute == ROUTE_MONITOR_POINTS ||
                    experimentalRoute == ROUTE_MONITOR_EXAMPLES
                ),
    ) {
        experimentalRoute = ROUTE_MONITOR_SETTINGS
    }
    BackHandler(
        enabled = selectedApp == null &&
            monitorPointKey == null &&
            monitorAppPackage == null &&
            (experimentalRoute == ROUTE_MONITOR_SETTINGS || experimentalRoute == ROUTE_WATCHERS),
    ) {
        experimentalRoute = ROUTE_EXPERIMENTAL
    }
    BackHandler(
        enabled = selectedApp == null &&
            monitorPointKey == null &&
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
    val monitorPointRows = monitorPointRows(
        uiState = experimentalUiState,
        apps = appListUiState.allApps,
    )
    val monitorPoint = monitorPointKey?.let { key ->
        monitorPointRows.firstOrNull { "${it.packageName}\n${it.operationName}" == key }
    }
    BackHandler(enabled = selectedApp == null && showSavedHistory) {
        showSavedHistory = false
    }
    if (selectedApp == null && showSavedHistory) {
        SavedHistoryScreen(
            uiState = settingsUiState,
            onBack = { showSavedHistory = false },
            onSaveIndividualHistoryChange = onSaveIndividualHistoryChange,
            countSavedHistory = countSavedHistory,
            onDeleteSavedHistory = onDeleteSavedHistory,
        )
        return
    }
    if (selectedApp == null && experimentalRoute != null) {
        when {
            // The detail page belongs to a row of the points list, so it takes
            // precedence over the route that opened that list.

            monitorApp != null -> MonitorOperationsScreen(
                app = monitorApp,
                selected = experimentalUiState
                    .selectionByPackage[monitorApp.packageName]
                    .orEmpty(),
                showAll = experimentalUiState.showAllOperations,
                warningSuppressed = experimentalUiState.allOperationsWarningSuppressed,
                onBack = { monitorAppPackage = null },
                onSelectionChange = { operations ->
                    onMonitorOperationsChange(monitorApp.packageName, operations)
                },
                onShowAllChange = onMonitorShowAllOperationsChange,
                onSuppressWarning = onMonitorSuppressAllOperationsWarning,
            )

            experimentalRoute == ROUTE_MONITOR_TARGETS -> MonitorTargetsScreen(
                apps = appListUiState.allApps,
                selectionByPackage = experimentalUiState.selectionByPackage,
                onBack = { experimentalRoute = ROUTE_MONITOR_SETTINGS },
                onAppSelected = { app -> monitorAppPackage = app.packageName },
            )

            monitorPoint != null -> MonitorPointDetailScreen(
                point = monitorPoint,
                onBack = { monitorPointKey = null },
                onThrottleChange = { seconds ->
                    onMonitorThrottleChange(
                        monitorPoint.packageName,
                        monitorPoint.operationName,
                        seconds,
                    )
                },
                onHeadsUpChange = { headsUp ->
                    onMonitorPointHeadsUpChange(
                        monitorPoint.packageName,
                        monitorPoint.operationName,
                        headsUp,
                    )
                },
                onOutcomesChange = { outcomes ->
                    onMonitorPointOutcomesChange(
                        monitorPoint.packageName,
                        monitorPoint.operationName,
                        outcomes,
                    )
                },
                onBackgroundOnlyChange = { backgroundOnly ->
                    onMonitorPointBackgroundOnlyChange(
                        monitorPoint.packageName,
                        monitorPoint.operationName,
                        backgroundOnly,
                    )
                },
            )

            experimentalRoute == ROUTE_MONITOR_EXAMPLES -> MonitorExamplesScreen(
                onBack = { experimentalRoute = ROUTE_MONITOR_SETTINGS },
            )

            experimentalRoute == ROUTE_MONITOR_POINTS -> MonitorPointsScreen(
                points = monitorPointRows,
                onBack = { experimentalRoute = ROUTE_MONITOR_SETTINGS },
                onPointSelected = { row ->
                    monitorPointKey = "${row.packageName}\n${row.operationName}"
                },
            )

            experimentalRoute == ROUTE_MONITOR_SETTINGS -> MonitorSettingsScreen(
                uiState = experimentalUiState,
                onBack = { experimentalRoute = ROUTE_EXPERIMENTAL },
                onOpenTargets = { experimentalRoute = ROUTE_MONITOR_TARGETS },
                onOpenPoints = { experimentalRoute = ROUTE_MONITOR_POINTS },
                onOpenExamples = { experimentalRoute = ROUTE_MONITOR_EXAMPLES },
                onHeadsUpChange = onMonitorHeadsUpChange,
            )

            experimentalRoute == ROUTE_WATCHERS -> WatchersScreen(
                uiState = watchersUiState,
                onRefresh = onRefreshWatchers,
                onBack = { experimentalRoute = ROUTE_EXPERIMENTAL },
            )

            else -> ExperimentalScreen(
                uiState = experimentalUiState,
                onBack = { experimentalRoute = null },
                onMonitorChange = onMonitorEnabledChange,
                onEnableUnconfirmedMonitor = onEnableUnconfirmedMonitor,
                onOpenWatchers = { experimentalRoute = ROUTE_WATCHERS },
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
                        timeRange = effectiveHistoryRange,
                        onTimeRangeChange = { historyTimeRange = it },
                        onRefresh = onRefreshHistory,
                        onPermissionSelected = { permission ->
                            selectedHistoryPermissionName =
                                permission.shellOperationName
                            showHistoryAppStatistics = false
                            historyAppFilter = null
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
                        }?.let {
                            HistoryFilter.apply(
                                it, effectiveHistoryRange, null,
                                System.currentTimeMillis(), ZoneId.systemDefault(),
                            )
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
                        savingIndividualRecords = historyUiState.saveIndividualHistory,
                        timeRange = effectiveHistoryRange,
                        onTimeRangeChange = { historyTimeRange = it },
                        appFilter = historyAppFilter,
                        onAppFilterChange = { historyAppFilter = it },
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
                onOpenSavedHistory = { showSavedHistory = true },
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
private const val ROUTE_WATCHERS = "watchers"
private const val ROUTE_MONITOR_SETTINGS = "monitor_settings"
private const val ROUTE_MONITOR_TARGETS = "monitor_targets"
private const val ROUTE_MONITOR_POINTS = "monitor_points"
private const val ROUTE_MONITOR_EXAMPLES = "monitor_examples"

/**
 * Orders the points so the ones that can act come first, and names them with
 * labels rather than package names.
 */
@Composable
private fun monitorPointRows(
    uiState: ExperimentalUiState,
    apps: List<InstalledApp>,
): List<MonitorPointRow> {
    val labels = apps.associate { it.packageName to it.label }
    return uiState.configurablePoints
        .map { settings ->
            MonitorPointRow(
                settings = settings,
                appLabel = labels[settings.packageName] ?: settings.packageName,
                operationLabel = AppOpDisplayCatalog.labelResOf(settings.operationName)
                    ?.let { stringResource(it) }
                    ?: settings.operationName,
                watched = uiState.isWatched(settings.packageName, settings.operationName),
            )
        }
        .sortedWith(
            compareByDescending(MonitorPointRow::watched)
                .thenBy(MonitorPointRow::appLabel)
                .thenBy(MonitorPointRow::operationLabel),
        )
}
