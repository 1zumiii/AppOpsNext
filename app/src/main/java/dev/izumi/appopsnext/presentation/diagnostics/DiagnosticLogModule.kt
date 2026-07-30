package dev.izumi.appopsnext.presentation.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R

@Composable
fun DiagnosticLogModule(
    uiState: DiagnosticsUiState,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showReport by remember { mutableStateOf(false) }
    ListItem(
        modifier = modifier.clickable { showReport = true },
        headlineContent = {
            Text(text = stringResource(R.string.diagnostic_log_title))
        },
        supportingContent = {
            Text(
                text = stringResource(
                    R.string.diagnostic_log_summary,
                    uiState.diagnosticEventCount,
                ),
            )
        },
    )

    if (showReport) {
        DiagnosticReportDialog(
            report = uiState.diagnosticReport,
            onClear = onClear,
            onDismiss = { showReport = false },
        )
    }
}

@Composable
private fun DiagnosticReportDialog(
    report: String,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.diagnostic_report_title))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.diagnostic_report_privacy_warning,
                    ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.small,
                ) {
                    SelectionContainer {
                        Text(
                            text = report,
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                TextButton(onClick = onClear) {
                    Text(text = stringResource(R.string.diagnostic_log_clear))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    copyDiagnosticReport(context, report)
                },
                enabled = report.isNotBlank(),
            ) {
                Text(text = stringResource(R.string.diagnostic_report_copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_dismiss))
            }
        },
    )
}

private fun copyDiagnosticReport(context: Context, report: String) {
    val clipboard =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(
        ClipData.newPlainText(
            context.getString(R.string.diagnostic_report_clip_label),
            report,
        ),
    )
    Toast.makeText(
        context,
        R.string.diagnostic_report_copied,
        Toast.LENGTH_SHORT,
    ).show()
}
