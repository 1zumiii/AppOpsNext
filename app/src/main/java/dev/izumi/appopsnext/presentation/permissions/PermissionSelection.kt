package dev.izumi.appopsnext.presentation.permissions

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpNames
import dev.izumi.appopsnext.appops.parser.OpStates
import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.batch.model.BatchOperationTarget

/** Builds from eligible apps, not search results, so hidden search selections are retained. */
fun permissionBatchTargets(
    apps: List<InstalledApp>,
    states: OpStates,
    selectedPackages: Set<String>,
    operation: String,
    requestedMode: AppOpMode,
): List<BatchOperationTarget> = apps.mapNotNull { app ->
    if (app.packageName !in selectedPackages) return@mapNotNull null
    val effective = states.effective(app.uid, app.packageName)
    if (effective.mode == null) return@mapNotNull null
    BatchOperationTarget(
        packageName = app.packageName,
        appLabel = app.label,
        uid = app.uid,
        stableOperationName = AppOpNames.stableName(operation),
        preferredScope = effective.scope,
        requestedMode = requestedMode,
    )
}
