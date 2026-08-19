package dev.izumi.appopsnext.newapps

import dev.izumi.appopsnext.batch.model.BatchOperationTarget
import dev.izumi.appopsnext.newapps.model.InstalledPackageRecord
import dev.izumi.appopsnext.templates.model.PermissionTemplate

object NewAppPolicyTargetFactory {
    fun create(
        app: InstalledPackageRecord,
        template: PermissionTemplate,
    ): List<BatchOperationTarget> = template.rules.map { rule ->
        BatchOperationTarget(
            packageName = app.fingerprint.packageName,
            appLabel = app.label,
            stableOperationName = rule.stableOperationName,
            scope = rule.scope,
            requestedMode = rule.mode,
        )
    }
}
