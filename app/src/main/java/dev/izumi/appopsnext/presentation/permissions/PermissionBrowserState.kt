package dev.izumi.appopsnext.presentation.permissions

import dev.izumi.appopsnext.appops.parser.OpStates

data class PermissionBrowserState(
    val operation: String? = null,
    val states: OpStates? = null,
    val loading: Boolean = false,
    val failed: Boolean = false,
)
