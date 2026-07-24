package dev.izumi.appopsnext.presentation.app_list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.presentation.batch.TemplatePickerDialog
import dev.izumi.appopsnext.presentation.components.AppIcon
import dev.izumi.appopsnext.templates.model.PermissionTemplate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    uiState: AppListUiState,
    onSearchQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onAppSelected: (InstalledApp) -> Unit,
    templates: List<PermissionTemplate>,
    onTemplateApplyRequested:
        (PermissionTemplate, List<InstalledApp>) -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
) {
    var batchSelectionMode by remember { mutableStateOf(false) }
    var selectedPackages by remember {
        mutableStateOf(emptySet<String>())
    }
    var showTemplatePicker by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_list_title),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            batchSelectionMode = !batchSelectionMode
                            selectedPackages = emptySet()
                        },
                    ) {
                        Icon(
                            painter = painterResource(
                                if (batchSelectionMode) {
                                    R.drawable.ic_action_close
                                } else {
                                    R.drawable.ic_action_batch
                                },
                            ),
                            contentDescription = stringResource(
                                if (batchSelectionMode) {
                                    R.string.batch_cancel_selection
                                } else {
                                    R.string.batch_action
                                },
                            ),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = bottomBar,
    ) { contentPadding ->
        when {
            uiState.isLoading && uiState.visibleApps.isEmpty() -> LoadingContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )

            uiState.loadFailed && uiState.visibleApps.isEmpty() -> LoadFailureContent(
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )

            else -> AppListContent(
                uiState = uiState,
                onSearchQueryChange = onSearchQueryChange,
                onAppSelected = onAppSelected,
                batchSelectionMode = batchSelectionMode,
                selectedPackages = selectedPackages,
                onBatchSelectionChange = { packageName, selected ->
                    selectedPackages = if (selected) {
                        selectedPackages + packageName
                    } else {
                        selectedPackages - packageName
                    }
                },
                onApplyTemplate = { showTemplatePicker = true },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )
        }
    }

    if (showTemplatePicker) {
        TemplatePickerDialog(
            templates = templates,
            onSelect = { template ->
                showTemplatePicker = false
                onTemplateApplyRequested(
                    template,
                    uiState.allApps.filter {
                        it.packageName in selectedPackages
                    },
                )
                batchSelectionMode = false
                selectedPackages = emptySet()
            },
            onDismiss = { showTemplatePicker = false },
        )
    }
}

@Composable
private fun AppListContent(
    uiState: AppListUiState,
    onSearchQueryChange: (String) -> Unit,
    onAppSelected: (InstalledApp) -> Unit,
    batchSelectionMode: Boolean,
    selectedPackages: Set<String>,
    onBatchSelectionChange: (String, Boolean) -> Unit,
    onApplyTemplate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            label = {
                Text(text = stringResource(R.string.app_list_search_label))
            },
            singleLine = true,
        )
        Text(
            text = stringResource(
                R.string.app_list_count,
                uiState.visibleApps.size,
                uiState.totalAppCount,
            ),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (batchSelectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        R.string.batch_selected_count,
                        selectedPackages.size,
                    ),
                )
                FilledTonalButton(
                    onClick = onApplyTemplate,
                    enabled = selectedPackages.isNotEmpty(),
                ) {
                    Text(
                        text = stringResource(
                            R.string.batch_apply_template,
                        ),
                    )
                }
            }
        }
        if (uiState.visibleApps.isEmpty()) {
            EmptySearchContent(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(
                    items = uiState.visibleApps,
                    key = InstalledApp::packageName,
                ) { app ->
                    InstalledAppListItem(
                        app = app,
                        selectedForBatch = if (batchSelectionMode) {
                            app.packageName in selectedPackages
                        } else {
                            null
                        },
                        onClick = {
                            if (batchSelectionMode) {
                                onBatchSelectionChange(
                                    app.packageName,
                                    app.packageName !in selectedPackages,
                                )
                            } else {
                                onAppSelected(app)
                            }
                        },
                        onBatchSelectionChange = { selected ->
                            onBatchSelectionChange(
                                app.packageName,
                                selected,
                            )
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun InstalledAppListItem(
    app: InstalledApp,
    selectedForBatch: Boolean?,
    onClick: () -> Unit,
    onBatchSelectionChange: (Boolean) -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            AppIcon(
                packageName = app.packageName,
                appLabel = app.label,
            )
        },
        trailingContent = selectedForBatch?.let { selected ->
            {
                Checkbox(
                    checked = selected,
                    onCheckedChange = onBatchSelectionChange,
                )
            }
        },
        headlineContent = {
            Text(
                text = app.label,
                fontWeight = FontWeight.Medium,
            )
        },
        supportingContent = {
            Text(text = app.packageName)
        },
        overlineContent = {
            Text(
                text = stringResource(
                    if (app.isSystemApp) {
                        R.string.app_type_system
                    } else {
                        R.string.app_type_user
                    },
                    app.uid,
                ),
            )
        },
    )
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun LoadFailureContent(
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.app_list_load_failed),
            style = MaterialTheme.typography.titleMedium,
        )
        FilledTonalButton(
            onClick = onRefresh,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(text = stringResource(R.string.action_retry))
        }
    }
}

@Composable
private fun EmptySearchContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.app_list_empty_search),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
