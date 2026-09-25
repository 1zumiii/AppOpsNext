package dev.izumi.appopsnext.presentation.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.BuildConfig
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.ui.theme.mainPageHeadingWeight
import dev.izumi.appopsnext.appops.model.AppOpsReadState
import dev.izumi.appopsnext.presentation.diagnostics.DiagnosticLogModule
import dev.izumi.appopsnext.presentation.diagnostics.DiagnosticsSection
import dev.izumi.appopsnext.presentation.diagnostics.DiagnosticsUiState
import dev.izumi.appopsnext.presentation.components.MainPageEntryIcon
import dev.izumi.appopsnext.presentation.components.MainPageChevron
import dev.izumi.appopsnext.presentation.components.StatusGlyphBlock
import dev.izumi.appopsnext.presentation.components.StatusVisual
import dev.izumi.appopsnext.settings.AppLanguage
import dev.izumi.appopsnext.shizuku.model.PrivilegedBackendType
import dev.izumi.appopsnext.shizuku.model.PrivilegedServiceState
import dev.izumi.appopsnext.shizuku.model.ShizukuState

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
    onShizukuAction: () -> Unit,
    onPrivilegedServiceRetry: () -> Unit,
    onClearDiagnosticLog: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
) {
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showConnectionDetails by remember { mutableStateOf(false) }
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
                            Switch(
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
                        supportingContent = {
                            Text(text = appLanguageLabel(uiState.appLanguage))
                        },
                        trailingContent = { MainPageChevron() },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            // Authorizing Shizuku is the first thing a new installation needs, so the
            // connection sits right after the general settings rather than near the end.
            item {
                SettingsSectionTitle(
                    text = stringResource(R.string.settings_diagnostics),
                )
            }
            item {
                SettingsGroup {
                    val connection = connectionSummary(diagnosticsUiState)
                    ListItem(
                        modifier = Modifier.clickable {
                            showConnectionDetails = !showConnectionDetails
                        },
                        leadingContent = {
                            StatusGlyphBlock(status = connection.second)
                        },
                        headlineContent = {
                            Text(text = stringResource(R.string.settings_connection_summary_title))
                        },
                        supportingContent = { Text(text = connection.first) },
                        trailingContent = {
                            Text(
                                text = stringResource(
                                    if (showConnectionDetails) {
                                        R.string.settings_connection_hide_details
                                    } else {
                                        R.string.settings_connection_show_details
                                    },
                                ),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
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
                SettingsSectionTitle(
                    text = stringResource(R.string.settings_history_section),
                )
            }
            item {
                SettingsGroup {
                    SavedHistoryEntry(uiState = uiState, onOpen = onOpenSavedHistory)
                }
            }
            item {
                SettingsSectionTitle(
                    text = stringResource(R.string.settings_experimental),
                )
            }
            item {
                SettingsGroup {
                    ListItem(
                        modifier = Modifier.clickable(onClick = onOpenExperimental),
                        leadingContent = {
                            MainPageEntryIcon(R.drawable.ic_ph_bell_ringing)
                        },
                        headlineContent = {
                            Text(text = stringResource(R.string.experimental_title))
                        },
                        supportingContent = {
                            Text(text = stringResource(R.string.experimental_caption))
                        },
                        trailingContent = { MainPageChevron() },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            item {
                SettingsSectionTitle(
                    text = stringResource(R.string.settings_testing),
                )
            }
            item {
                SettingsGroup {
                    DiagnosticLogModule(
                        uiState = diagnosticsUiState,
                        onClear = onClearDiagnosticLog,
                    )
                }
            }
            item {
                SettingsSectionTitle(
                    text = stringResource(R.string.settings_about),
                )
            }
            item {
                SettingsGroup {
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
            }
            item {
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (showLanguageDialog) {
        AlertDialog(
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
                TextButton(onClick = { showLanguageDialog = false }) {
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
        modifier = Modifier.padding(start = 24.dp, end = 20.dp, top = 20.dp, bottom = 10.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = mainPageHeadingWeight(),
        style = MaterialTheme.typography.titleSmall,
    )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsGroupDivider() {
    Spacer(Modifier.height(4.dp))
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
