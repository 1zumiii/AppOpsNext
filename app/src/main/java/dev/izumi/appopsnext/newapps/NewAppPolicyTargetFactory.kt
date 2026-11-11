package dev.izumi.appopsnext.newapps

import dev.izumi.appopsnext.appops.changes.AppOpChangeRecord
import dev.izumi.appopsnext.appops.model.AppOpScope
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
            uid = app.uid,
            stableOperationName = rule.stableOperationName,
            preferredScope = AppOpScope.PACKAGE,
            requestedMode = rule.mode,
        )
    }

    /**
     * The settings [app] had before it was installed again, as [restorable]
     * holds them, one per operation. They are written at package scope whatever
     * scope they had: the write falls back to the UID where it is safe to.
     */
    fun restore(
        app: InstalledPackageRecord,
        restorable: List<AppOpChangeRecord>,
    ): List<BatchOperationTarget> = restorable.map { record ->
        BatchOperationTarget(
            packageName = app.fingerprint.packageName,
            appLabel = app.label,
            uid = app.uid,
            stableOperationName = record.stableOperationName,
            preferredScope = AppOpScope.PACKAGE,
            requestedMode = record.recordedMode,
        )
    }
}
