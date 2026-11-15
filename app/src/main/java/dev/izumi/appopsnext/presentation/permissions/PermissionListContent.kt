package dev.izumi.appopsnext.presentation.permissions

import androidx.compose.foundation.lazy.rememberLazyListState
import dev.izumi.appopsnext.presentation.components.EmptyStateIcons
import dev.izumi.appopsnext.presentation.components.EmptyStateContent
import dev.izumi.appopsnext.presentation.components.topFadingEdge
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.izumi.appopsnext.AppOpsNextApplication
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.presentation.app_detail.AppOpDisplayCatalog
import dev.izumi.appopsnext.presentation.app_detail.AppOpDisplayItem
import dev.izumi.appopsnext.presentation.app_detail.appOpDisplayLabel
import dev.izumi.appopsnext.presentation.components.rememberMiuiOpCodes
import dev.izumi.appopsnext.presentation.components.FavoriteHintAnchor
import dev.izumi.appopsnext.presentation.components.FavoriteOperationRow
import dev.izumi.appopsnext.presentation.components.FavoriteOperations
import dev.izumi.appopsnext.presentation.components.FavoriteOperationsState
import dev.izumi.appopsnext.presentation.components.MainPageSectionTitle
import dev.izumi.appopsnext.presentation.components.rememberFavoriteOperations
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.SnackbarHost
import androidx.compose.ui.Alignment
import dev.izumi.appopsnext.appops.model.MiuiOps
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
    val favorites = rememberFavoriteOperations()
    // Searching shows plain matches. Otherwise frequently used permissions come first.
    val (favoriteRows, otherRows) = remember(permissions, favorites.favorites, searchQuery) {
        if (searchQuery.isNotBlank()) {
            emptyList<AppOpDisplayItem>() to permissions
        } else {
            FavoriteOperations.split(permissions, favorites.favorites, AppOpDisplayItem::operationName)
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            CompactSearchField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = stringResource(R.string.app_detail_search_label),
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 12.dp),
            )
            val listState = rememberLazyListState()
            LazyColumn(
                modifier = Modifier.weight(1f).topFadingEdge(listState),
                state = listState,
                // The top padding is short by the anchor below and the gap after it.
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 3.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // The list keeps its place by the first visible item. With this
                // anchor first, the frequently used group, which can appear above
                // the rows after the settings load or a row is added, stays in view.
                // It is 1 dp tall, since an empty item does not count as visible.
                item(key = "anchor:top") { Spacer(modifier = Modifier.fillMaxWidth().height(1.dp)) }
                if (permissions.isEmpty()) {
                    item {
                        EmptyStateContent(
                            icon = EmptyStateIcons.NO_MATCHES,
                            title = stringResource(R.string.app_detail_no_matching_operations),
                            modifier = Modifier.fillParentMaxSize(),
                        )
                    }
                }
                if (favoriteRows.isNotEmpty()) {
                    item(key = "header:favorites") {
                        MainPageSectionTitle(
                            text = stringResource(R.string.favorite_section_title),
                            horizontalPadding = 4.dp,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                items(items = favoriteRows, key = AppOpDisplayItem::operationName) { permission ->
                    PermissionRow(
                        permission = permission,
                        favorites = favorites,
                        onPermissionSelected = onPermissionSelected,
                        modifier = Modifier.animateItem(),
                    )
                }
                if (favoriteRows.isNotEmpty() && otherRows.isNotEmpty()) {
                    item(key = "header:rest") {
                        MainPageSectionTitle(
                            text = stringResource(R.string.favorite_section_rest),
                            horizontalPadding = 4.dp,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                itemsIndexed(items = otherRows, key = { _, item -> item.operationName }) { index, permission ->
                    val row = @Composable {
                        PermissionRow(
                            permission = permission,
                            favorites = favorites,
                            onPermissionSelected = onPermissionSelected,
                        )
                    }
                    Box(modifier = Modifier.animateItem()) {
                        if (index == 0 && favoriteRows.isEmpty() && searchQuery.isBlank()) {
                            FavoriteHintAnchor(state = favorites, content = row)
                        } else {
                            row()
                        }
                    }
                }
            }
        }
        SnackbarHost(
            hostState = favorites.snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun PermissionRow(
    permission: AppOpDisplayItem,
    favorites: FavoriteOperationsState,
    onPermissionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FavoriteOperationRow(
        operationName = permission.operationName,
        state = favorites,
        enabled = true,
        modifier = modifier,
        onClick = { onPermissionSelected(permission.operationName) },
        shape = RoundedCornerShape(16.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            ListItem(
                headlineContent = {
                    Text(
                        text = permission.labelRes?.let { stringResource(it) }
                            ?: appOpDisplayLabel(permission.operationName),
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

@Composable
internal fun permissionLabel(operation: String): String = appOpDisplayLabel(operation)

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
    val structuredReads = (context.applicationContext as AppOpsNextApplication).structuredAppOpsReads
    // The probe can finish after this screen opens; the list then gains the device's extra operations.
    val readCapabilities by structuredReads.enabled.collectAsStateWithLifecycle()
    // MIUI private operations are absent from the platform's table, so they
    // join the list once the device has shown that it has them.
    val miuiCodes = rememberMiuiOpCodes()
    return remember(query, context, alternateContext, readCapabilities, miuiCodes) {
        AppOpDisplayCatalog.build(
            entries = emptyList(),
            query = query,
            labelResolver = context::getString,
            alternateLabelResolver = alternateContext::getString,
            extraOperations = structuredReads.deviceOps().orEmpty().map { it.shellName } +
                miuiCodes.sorted().mapNotNull(MiuiOps::nameOf),
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
                Text(text = stringResource(R.string.permission_info_favorites))
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_dismiss))
            }
        },
    )
}
