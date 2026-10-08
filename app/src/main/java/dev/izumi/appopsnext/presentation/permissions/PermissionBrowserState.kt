package dev.izumi.appopsnext.presentation.permissions

import dev.izumi.appopsnext.appops.parser.OpStates

data class PermissionBrowserState(
    val operation: String? = null,
    val states: OpStates? = null,
    val loading: Boolean = false,
    val failed: Boolean = false,
    /** Missing entries mean unavailable or not a mapped runtime permission. */
    val runtimeGrants: Map<String, Boolean> = emptyMap(),
)
