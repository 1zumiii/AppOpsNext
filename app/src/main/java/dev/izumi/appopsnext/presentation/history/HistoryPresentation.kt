package dev.izumi.appopsnext.presentation.history

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.izumi.appopsnext.appops.model.AppOpNames
import dev.izumi.appopsnext.history.model.HistoryPermission
import dev.izumi.appopsnext.presentation.app_detail.AppOpDisplayCatalog

@StringRes
fun HistoryPermission.labelResource(): Int? =
    LabelResourcesByShellName[shellOperationName]

fun HistoryPermission.systemOperationName(): String =
    AppOpNames.stableName(shellOperationName)

@Composable
fun HistoryPermission.displayName(): String =
    labelResource()?.let { stringResource(it) }
        ?: systemOperationName()

private val LabelResourcesByShellName =
    AppOpDisplayCatalog.knownOperations().associate {
        it.shellName to it.labelRes
    }
