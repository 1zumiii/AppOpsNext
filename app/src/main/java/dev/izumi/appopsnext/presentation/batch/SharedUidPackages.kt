package dev.izumi.appopsnext.presentation.batch

import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.batch.model.BatchOperationTarget

/**
 * Packages a batch changes without the user selecting them.
 *
 * A UID-scoped write reaches every package on that UID. Only a shared UID
 * reaches packages outside the selection, so only those siblings are listed.
 */
internal fun unselectedSharedUidPackages(
    targets: List<BatchOperationTarget>,
    packagesForUid: (Int) -> List<String>,
): List<String> {
    val selectedPackages = targets.map { it.packageName }.toSet()
    return targets
        .filter { it.preferredScope == AppOpScope.UID }
        .map { it.uid }
        .distinct()
        .flatMap { uid -> packagesForUid(uid).takeIf { it.size > 1 }.orEmpty() }
        .filter { it !in selectedPackages }
        .distinct()
        .sorted()
}
