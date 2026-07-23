package dev.izumi.appopsnext.presentation.batch

import dev.izumi.appopsnext.appops.model.AppOpScope

data class PermissionBatchSelection(
    val operationName: String,
    val scope: AppOpScope,
)
