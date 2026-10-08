package dev.izumi.appopsnext.presentation.permissions

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import dev.izumi.appopsnext.presentation.components.AppBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.presentation.app_detail.AppOpDisplayCatalog
import dev.izumi.appopsnext.presentation.app_detail.AppOpDisplayItem
import dev.izumi.appopsnext.presentation.components.CompactSearchField
import dev.izumi.appopsnext.presentation.components.PermissionEntryIcon
import java.util.Locale

@Composable
fun PermissionListContent(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onPermissionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val permissions = rememberPermissionItems(searchQuery)
    Column(modifier = modifier.fillMaxSize()) {
        CompactSearchField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            label = stringResource(R.string.app_detail_search_label),
            modifier = Modifier.fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (permissions.isEmpty()) {
                item {
                    Text(text = stringResource(R.string.app_detail_no_matching_operations))
                }
            }
            items(items = permissions, key = AppOpDisplayItem::operationName) { permission ->
                Surface(
                    onClick = { onPermissionSelected(permission.operationName) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = permission.labelRes?.let { stringResource(it) }
                                    ?: permission.operationName,
                            )
                        },
                        supportingContent = { Text(text = permission.operationName) },
                        leadingContent = { PermissionEntryIcon(permission.operationName) },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
internal fun permissionLabel(operation: String): String {
    val metadata = AppOpDisplayCatalog.knownOperations().firstOrNull {
        it.shellName == operation
    }
    return metadata?.let { stringResource(it.labelRes) } ?: operation
}

@Composable
private fun rememberPermissionItems(query: String): List<AppOpDisplayItem> {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val alternateContext = remember(context, configuration) {
        context.createConfigurationContext(
            Configuration(configuration).apply {
                setLocale(
                    if (configuration.locales[0]?.language == Locale.ENGLISH.language) {
                        Locale.SIMPLIFIED_CHINESE
                    } else {
                        Locale.ENGLISH
                    },
                )
            },
        )
    }
    return remember(query, context, alternateContext) {
        AppOpDisplayCatalog.build(
            entries = emptyList(),
            query = query,
            labelResolver = context::getString,
            alternateLabelResolver = alternateContext::getString,
        )
    }
}

@Composable
fun PermissionInfoDialog(onDismiss: () -> Unit) {
    AppBottomSheet(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.permission_info_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = stringResource(R.string.permission_info_modes))
                Text(text = stringResource(R.string.permission_info_grants))
                Text(text = stringResource(R.string.permission_info_scope))
                Text(text = stringResource(R.string.permission_info_fallback))
                Text(text = stringResource(R.string.permission_info_selection))
                Text(text = stringResource(R.string.permission_info_unknown))
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_dismiss))
            }
        },
    )
}
