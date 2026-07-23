package dev.izumi.appopsnext.presentation.batch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.templates.model.PermissionTemplate

@Composable
fun TemplatePickerDialog(
    templates: List<PermissionTemplate>,
    onSelect: (PermissionTemplate) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.batch_choose_template))
        },
        text = {
            if (templates.isEmpty()) {
                Text(
                    text = stringResource(R.string.batch_no_templates),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                ) {
                    items(
                        items = templates,
                        key = PermissionTemplate::id,
                    ) { template ->
                        ListItem(
                            modifier = Modifier.clickable {
                                onSelect(template)
                            },
                            headlineContent = {
                                Text(
                                    text = template.name,
                                    fontWeight = FontWeight.Medium,
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = stringResource(
                                        R.string.template_rule_count,
                                        template.rules.size,
                                    ),
                                )
                            },
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
