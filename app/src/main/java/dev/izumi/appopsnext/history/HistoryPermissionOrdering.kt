package dev.izumi.appopsnext.history

import dev.izumi.appopsnext.history.model.HistoryPermission

object HistoryPermissionOrdering {
    fun mergeSelection(
        current: List<HistoryPermission>,
        requestedOperationNames: List<String>,
        available: List<HistoryPermission>,
    ): List<HistoryPermission> {
        val requestedNames = requestedOperationNames.toSet()
        val retained = current.filter {
            it.shellOperationName in requestedNames
        }
        val retainedNames = retained.mapTo(mutableSetOf()) {
            it.shellOperationName
        }
        val availableByName = available.associateBy {
            it.shellOperationName
        }
        val added = requestedOperationNames.mapNotNull { operationName ->
            availableByName[operationName]
                ?.takeIf { retainedNames.add(operationName) }
        }
        return retained + added
    }

    fun reorder(
        current: List<HistoryPermission>,
        orderedOperationNames: List<String>,
    ): List<HistoryPermission>? {
        val permissionsByName = current.associateBy {
            it.shellOperationName
        }
        if (orderedOperationNames.toSet() != permissionsByName.keys) {
            return null
        }
        return orderedOperationNames.mapNotNull(permissionsByName::get)
    }
}
