package dev.izumi.appopsnext.presentation.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.history.model.HistoryPermission

@Composable
fun HistoryPermissionPickerDialog(
    availablePermissions: List<HistoryPermission>,
    selectedPermissions: Set<String>,
    onSelect: (HistoryPermission) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val options = availablePermissions.filter { permission ->
        permission.shellOperationName !in selectedPermissions &&
            (
                query.isBlank() ||
                    permission.shellOperationName.contains(
                        query,
                        ignoreCase = true,
                    ) ||
                    permission.systemOperationName().contains(
                        query,
                        ignoreCase = true,
                    ) ||
                    permission.displayName().contains(
                        query,
                        ignoreCase = true,
                    )
                )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.history_add_permission))
        },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
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
                                    R.string.history_no_permissions_to_add,
                                ),
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    items(
                        items = options,
                        key = HistoryPermission::shellOperationName,
                    ) { permission ->
                        ListItem(
                            modifier = Modifier.clickable {
                                onSelect(permission)
                            },
                            headlineContent = {
                                Text(text = permission.displayName())
                            },
                            supportingContent = {
                                Text(
                                    text = permission.systemOperationName(),
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
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}
