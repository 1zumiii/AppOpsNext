package dev.izumi.appopsnext.history

import dev.izumi.appopsnext.appops.model.AppOpNames
import dev.izumi.appopsnext.history.model.HistoryPermission
import dev.izumi.appopsnext.history.model.HistoryPermissionDefaults

object HistoryPermissionSelectionCodec {
    fun decode(value: String?): List<HistoryPermission> {
        if (value == null) return HistoryPermissionDefaults.permissions
        return value
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map(AppOpNames::shellName)
            .distinct()
            .map(::HistoryPermission)
            .toList()
    }

    fun encode(permissions: List<HistoryPermission>): String =
        permissions.joinToString(separator = "\n") {
            it.shellOperationName
        }
}
