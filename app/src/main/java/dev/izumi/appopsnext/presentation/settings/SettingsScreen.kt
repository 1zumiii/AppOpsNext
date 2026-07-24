package dev.izumi.appopsnext.presentation.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.presentation.diagnostics.DiagnosticsSection
import dev.izumi.appopsnext.presentation.diagnostics.DiagnosticsUiState
import dev.izumi.appopsnext.settings.AppLanguage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    diagnosticsUiState: DiagnosticsUiState,
    onHideSystemAppsChange: (Boolean) -> Unit,
    onAppLanguageChange: (AppLanguage) -> Unit,
    onShizukuAction: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
) {
    var showLanguageDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val githubUrl = stringResource(R.string.settings_github_url)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        fontWeight = FontWeight.SemiBold,
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
                ListItem(
                    modifier = Modifier.clickable {
                        onHideSystemAppsChange(!uiState.hideSystemApps)
                    },
                    headlineContent = {
                        Text(
                            text = stringResource(
                                R.string.settings_hide_system_apps,
                            ),
                        )
                    },
                    supportingContent = {
                        Text(
                            text = stringResource(
                                R.string.settings_hide_system_apps_detail,
                            ),
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = uiState.hideSystemApps,
                            onCheckedChange = onHideSystemAppsChange,
                        )
                    },
                )
            }
            item {
                ListItem(
                    modifier = Modifier.clickable {
                        showLanguageDialog = true
                    },
                    headlineContent = {
                        Text(text = stringResource(R.string.settings_language))
                    },
                    supportingContent = {
                        Text(text = appLanguageLabel(uiState.appLanguage))
                    },
                )
            }
            item {
                SettingsSectionTitle(
                    text = stringResource(R.string.settings_diagnostics),
                )
            }
            item {
                DiagnosticsSection(
                    uiState = diagnosticsUiState,
                    onShizukuAction = onShizukuAction,
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 4.dp,
                    ),
                )
            }
            item {
                SettingsSectionTitle(
                    text = stringResource(R.string.settings_about),
                )
            }
            item {
                ListItem(
                    headlineContent = {
                        Text(text = stringResource(R.string.settings_developer))
                    },
                    supportingContent = {
                        Text(
                            text = stringResource(
                                R.string.settings_developer_name,
                            ),
                        )
                    },
                )
            }
            item {
                ListItem(
                    modifier = Modifier.clickable {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl)),
                            )
                        }
                    },
                    headlineContent = {
                        Text(text = stringResource(R.string.settings_github))
                    },
                    supportingContent = {
                        Text(text = githubUrl)
                    },
                )
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
private fun SettingsSectionTitle(text: String) {
    Column {
        HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 12.dp,
            ),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
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
