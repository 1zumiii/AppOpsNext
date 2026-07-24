package dev.izumi.appopsnext.presentation.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.history.model.HistoryPermission

@Composable
fun HistoryPermissionManagementDialog(
    availablePermissions: List<HistoryPermission>,
    selectedPermissions: Set<String>,
    onApply: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var draftSelection by remember(selectedPermissions) {
        mutableStateOf(selectedPermissions)
    }
    val context = LocalContext.current
    val options = availablePermissions.filter { permission ->
        val localizedName = permission.labelResource()
            ?.let(context::getString)
            ?: permission.systemOperationName()
        query.isBlank() ||
            permission.shellOperationName.contains(
                query,
                ignoreCase = true,
            ) ||
            permission.systemOperationName().contains(
                query,
                ignoreCase = true,
            ) ||
            localizedName.contains(query, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.history_manage_permissions))
        },
        text = {
            Column {
                Text(
                    text = stringResource(
                        R.string.history_management_selected_count,
                        draftSelection.size,
                    ),
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    label = {
                        Text(
                            text = stringResource(
                                R.string.history_permission_search,
                            ),
                        )
                    },
                    singleLine = true,
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .padding(top = 8.dp),
                ) {
                    if (options.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(
                                    R.string.history_no_matching_permissions,
                                ),
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    items(
                        items = options,
                        key = HistoryPermission::shellOperationName,
                    ) { permission ->
                        val operationName =
                            permission.shellOperationName
                        val selected = operationName in draftSelection
                        ListItem(
                            modifier = Modifier.clickable {
                                draftSelection = draftSelection.toggle(
                                    operationName,
                                )
                            },
                            headlineContent = {
                                Text(text = permission.displayName())
                            },
                            supportingContent = {
                                Text(
                                    text = permission.systemOperationName(),
                                )
                            },
                            trailingContent = {
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = {
                                        draftSelection =
                                            draftSelection.toggle(
                                                operationName,
                                            )
                                    },
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(
                        availablePermissions
                            .map(HistoryPermission::shellOperationName)
                            .filter(draftSelection::contains),
                    )
                },
            ) {
                Text(text = stringResource(R.string.action_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value
