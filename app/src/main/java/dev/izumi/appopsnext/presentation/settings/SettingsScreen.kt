package dev.izumi.appopsnext.presentation.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import dev.izumi.appopsnext.presentation.components.CompactSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.BuildConfig
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.presentation.components.AppBottomSheet
import dev.izumi.appopsnext.ui.theme.mainPageHeadingWeight
import dev.izumi.appopsnext.appops.model.AppOpsReadState
import dev.izumi.appopsnext.presentation.diagnostics.DiagnosticLogModule
import dev.izumi.appopsnext.presentation.diagnostics.DiagnosticsSection
import dev.izumi.appopsnext.presentation.diagnostics.DiagnosticsUiState
import dev.izumi.appopsnext.presentation.components.MainPageEntryIcon
import dev.izumi.appopsnext.presentation.components.MainPageChevron
import dev.izumi.appopsnext.presentation.components.StatusGlyph
import dev.izumi.appopsnext.presentation.components.MainPageGroupDivider
import dev.izumi.appopsnext.presentation.components.StatusVisual
import dev.izumi.appopsnext.settings.AppLanguage
import dev.izumi.appopsnext.settings.AppTheme
import dev.izumi.appopsnext.presentation.components.ThemeToggle
import dev.izumi.appopsnext.ui.theme.LocalThemeTransition
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import dev.izumi.appopsnext.shizuku.model.PrivilegedBackendType
import dev.izumi.appopsnext.shizuku.model.PrivilegedServiceState
import dev.izumi.appopsnext.shizuku.model.ShizukuState
import dev.izumi.appopsnext.update.UpdateState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    diagnosticsUiState: DiagnosticsUiState,
    onHideSystemAppsChange: (Boolean) -> Unit,
    onOpenSavedHistory: () -> Unit,
    onOpenExperimental: () -> Unit,
    onCheckForUpdate: () -> Unit,
    onAppLanguageChange: (AppLanguage) -> Unit,
    onAppThemeChange: (AppTheme) -> Unit,
    onShizukuAction: () -> Unit,
    onPrivilegedServiceRetry: () -> Unit,
    onClearDiagnosticLog: () -> Unit,
    onExportBackup: (Uri) -> Unit,
    onOpenBackup: (Uri) -> Unit,
    onConfirmBackupImport: () -> Unit,
    onDismissBackup: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
) {
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    val themeTransition = LocalThemeTransition.current
    var toggleCenter by remember { mutableStateOf(Offset.Unspecified) }
    var showConnectionDetails by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val githubUrl = stringResource(R.string.settings_github_url)
    val githubRepository = remember(githubUrl) {
        Uri.parse(githubUrl).path?.trim('/')?.takeIf(String::isNotBlank) ?: githubUrl
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        fontWeight = mainPageHeadingWeight(),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = bottomBar,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            item(key = "connection") {
                SettingsGroup {
                    val connection = connectionSummary(diagnosticsUiState)
                    ListItem(
                        modifier = Modifier.clickable { showConnectionDetails = !showConnectionDetails },
                        leadingContent = { StatusGlyph(connection.second, Modifier.size(24.dp)) },
                        headlineContent = { Text(stringResource(R.string.settings_connection_summary_title)) },
                        supportingContent = { Text(connection.first) },
                        trailingContent = { MainPageChevron() },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    if (showConnectionDetails) {
                        SettingsGroupDivider()
                        DiagnosticsSection(
                            uiState = diagnosticsUiState,
                            onShizukuAction = onShizukuAction,
                            onPrivilegedServiceRetry = onPrivilegedServiceRetry,
                        )
                    }
                }
            }
            item {
                SettingsSectionTitle(text = stringResource(R.string.settings_preferences))
            }
            item {
                SettingsGroup {
                    ListItem(
                        modifier = Modifier.clickable {
                            onHideSystemAppsChange(!uiState.hideSystemApps)
                        },
                        leadingContent = {
                            MainPageEntryIcon(R.drawable.ic_ph_squares_four)
                        },
                        headlineContent = {
                            Text(text = stringResource(R.string.settings_hide_system_apps))
                        },
                        supportingContent = {
                            Text(text = stringResource(R.string.settings_hide_system_apps_detail))
                        },
                        trailingContent = {
                            CompactSwitch(
                                checked = uiState.hideSystemApps,
                                onCheckedChange = onHideSystemAppsChange,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    SettingsGroupDivider()
                    ListItem(
                        modifier = Modifier.clickable { showLanguageDialog = true },
                        leadingContent = {
                            MainPageEntryIcon(R.drawable.ic_ph_translate)
                        },
                        headlineContent = {
                            Text(text = stringResource(R.string.settings_language))
                        },
                        trailingContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = appLanguageLabel(uiState.appLanguage),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                MainPageChevron()
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    SettingsGroupDivider()
                    ListItem(
                        modifier = Modifier.clickable { showThemeDialog = true },
                        leadingContent = {
                            MainPageEntryIcon(R.drawable.ic_ph_sun)
                        },
                        headlineContent = {
                            Text(text = stringResource(R.string.settings_theme))
                        },
                        supportingContent = {
                            Text(text = appThemeLabel(uiState.appTheme))
                        },
                        trailingContent = {
                            // Flips straight between light and dark. The row opens
                            // the full choice, which is the way back to following
                            // the system.
                            ThemeToggle(
                                dark = isSystemInDarkTheme(),
                                onDarkChange = { dark ->
                                    val theme = if (dark) AppTheme.DARK else AppTheme.LIGHT
                                    themeTransition?.switchTo(dark, toggleCenter) { onAppThemeChange(theme) }
                                        ?: onAppThemeChange(theme)
                                },
                                modifier = Modifier.onGloballyPositioned {
                                    toggleCenter = it.boundsInRoot().center
                                },
                                contentDescription = stringResource(R.string.settings_theme_dark),
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            item {
                SettingsSectionTitle(
                    text = stringResource(R.string.settings_records_automation),
                )
            }
            item {
                SettingsGroup {
                    SavedHistoryEntry(uiState = uiState, onOpen = onOpenSavedHistory)
                    SettingsGroupDivider()
                    ListItem(
                        modifier = Modifier.clickable(onClick = onOpenExperimental),
                        leadingContent = {
                            MainPageEntryIcon(R.drawable.ic_ph_flask)
                        },
                        headlineContent = {
                            Text(text = stringResource(R.string.experimental_title))
                        },
                        supportingContent = {
                            Text(text = stringResource(R.string.settings_experimental_summary))
                        },
                        trailingContent = { MainPageChevron() },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            item {
                SettingsSectionTitle(
                    text = stringResource(R.string.settings_data_support),
                )
            }
            item {
                SettingsGroup {
                    BackupEntries(
                        state = uiState.backup,
                        onExport = onExportBackup,
                        onOpen = onOpenBackup,
                        onConfirmImport = onConfirmBackupImport,
                        onDismiss = onDismissBackup,
                        compact = true,
                    )
                    SettingsGroupDivider()
                    DiagnosticLogModule(
                        uiState = diagnosticsUiState,
                        onClear = onClearDiagnosticLog,
                        compact = true,
                    )
                    SettingsGroupDivider()
                    ListItem(
                        modifier = Modifier.clickable { showAbout = true },
                        leadingContent = { MainPageEntryIcon(R.drawable.ic_ph_info) },
                        headlineContent = { Text(stringResource(R.string.settings_about_app)) },
                        supportingContent = {
                            if (uiState.updateState is UpdateState.Available) {
                                Text(
                                    stringResource(R.string.update_available_description),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        trailingContent = { MainPageChevron() },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            item {
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (showAbout) {
        AppBottomSheet(
            onDismissRequest = { showAbout = false },
            title = { Text(stringResource(R.string.settings_about_app)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    AppVersionRow(
                        updateState = uiState.updateState,
                        onOpenRelease = { url ->
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                                )
                            }
                        },
                        onCheckForUpdate = onCheckForUpdate,
                    )
                    SettingsGroupDivider()
                    ListItem(
                        leadingContent = {
                            MainPageEntryIcon(R.drawable.ic_ph_user)
                        },
                        headlineContent = {
                            Text(text = stringResource(R.string.settings_developer))
                        },
                        supportingContent = {
                            Text(text = stringResource(R.string.settings_developer_name))
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    SettingsGroupDivider()
                    ListItem(
                        modifier = Modifier.clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl)),
                                )
                            }
                        },
                        leadingContent = {
                            MainPageEntryIcon(R.drawable.ic_ph_link)
                        },
                        headlineContent = {
                            Text(text = stringResource(R.string.settings_github))
                        },
                        supportingContent = {
                            Text(text = githubRepository)
                        },
                        trailingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_ph_arrow_square_out),
                                contentDescription = stringResource(R.string.settings_open_external),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text(stringResource(R.string.action_dismiss)) }
            },
        )
    }

    if (showLanguageDialog) {
        AppBottomSheet(
            onDismissRequest = { showLanguageDialog = false },
            title = {
                Text(text = stringResource(R.string.settings_language))
            },
            text = {
                Column {
                    AppLanguage.entries.forEach { language ->
                        ListItem(
                            modifier = Modifier.clickable {
                                showLanguageDialog = false
                                onAppLanguageChange(language)
                            },
                            headlineContent = {
                                Text(text = appLanguageLabel(language))
                            },
                            leadingContent = {
                                RadioButton(
                                    selected =
                                        language == uiState.appLanguage,
                                    onClick = null,
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                            ),
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showLanguageDialog = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showThemeDialog) {
        AppBottomSheet(
            onDismissRequest = { showThemeDialog = false },
            title = {
                Text(text = stringResource(R.string.settings_theme))
            },
            text = {
                Column {
                    AppTheme.entries.forEach { theme ->
                        ListItem(
                            modifier = Modifier.clickable {
                                showThemeDialog = false
                                // Following the system plays no reveal: whether
                                // the look changes at all is up to the system.
                                val dark = when (theme) {
                                    AppTheme.LIGHT -> false
                                    AppTheme.DARK -> true
                                    AppTheme.SYSTEM -> null
                                }
                                if (dark != null && themeTransition != null) {
                                    themeTransition.switchTo(dark) { onAppThemeChange(theme) }
                                } else {
                                    onAppThemeChange(theme)
                                }
                            },
                            headlineContent = {
                                Text(text = appThemeLabel(theme))
                            },
                            leadingContent = {
                                RadioButton(
                                    selected = theme == uiState.appTheme,
                                    onClick = null,
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                            ),
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showThemeDialog = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun connectionSummary(uiState: DiagnosticsUiState): Pair<String, StatusVisual> {
    val service = uiState.privilegedServiceState
    return when {
        uiState.appOpsReadState is AppOpsReadState.Failure ->
            stringResource(R.string.status_appops_read_failed) to StatusVisual.ERROR
        service is PrivilegedServiceState.Connected -> {
            val backendName = stringResource(
                when (service.info.backendType) {
                    PrivilegedBackendType.NATIVE_DAEMON -> R.string.status_runtime_mode_native
                    PrivilegedBackendType.USER_SERVICE -> R.string.status_runtime_mode_user_service
                },
            )
            stringResource(R.string.settings_connection_connected, backendName) to
                StatusVisual.SUCCESS
        }
        service is PrivilegedServiceState.Connecting ||
            uiState.shizukuState is ShizukuState.Checking ->
            stringResource(R.string.status_connecting) to StatusVisual.NEUTRAL
        uiState.shizukuState is ShizukuState.PermissionRequired ->
            stringResource(R.string.status_permission_required) to StatusVisual.WARNING
        uiState.shizukuState is ShizukuState.PermissionDenied ->
            stringResource(R.string.status_permission_denied) to StatusVisual.ERROR
        service is PrivilegedServiceState.Failure ||
            uiState.shizukuState is ShizukuState.Failure ->
            stringResource(R.string.status_error) to StatusVisual.ERROR
        else -> stringResource(R.string.status_disconnected) to StatusVisual.WARNING
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 24.dp, end = 18.dp, top = 22.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = mainPageHeadingWeight(),
        style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
private fun SettingsGroup(
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        shape = RoundedCornerShape(18.dp),
        color = color,
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsGroupDivider() {
    MainPageGroupDivider()
}

@Composable
private fun appLanguageLabel(language: AppLanguage): String =
    stringResource(
        when (language) {
            AppLanguage.SYSTEM -> R.string.settings_language_system
            AppLanguage.SIMPLIFIED_CHINESE ->
                R.string.settings_language_chinese

            AppLanguage.ENGLISH -> R.string.settings_language_english
        },
    )

@Composable
private fun appThemeLabel(theme: AppTheme): String =
    stringResource(
        when (theme) {
            AppTheme.SYSTEM -> R.string.settings_language_system
            AppTheme.LIGHT -> R.string.settings_theme_light
            AppTheme.DARK -> R.string.settings_theme_dark
        },
    )
