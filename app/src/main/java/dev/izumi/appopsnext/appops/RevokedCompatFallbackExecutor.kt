package dev.izumi.appopsnext.appops

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpModeChangePhase
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.appops.model.AppOpsRestorationStatus

data class RevokedCompatFallbackOutcome(
    val result: AppOpModeChangeResult,
    /** The final result came from the permission flag, not from AppOps. */
    val flagApplied: Boolean,
    /**
     * Android kept a runtime-permission op unrestricted after an Ignore write.
     * AppOps can always restrict a granted permission unless the ROM derives
     * the op from the permission, so this marks such a ROM.
     */
    val restrictionBlocked: Boolean,
)

/**
 * Retries a rejected write through the permission's `REVOKED_COMPAT` flag.
 *
 * Some Android 16 builds derive runtime-permission app ops from the permission
 * and drop every UID and package write for them. The flag is the remaining
 * way to switch such an op between Ignore and its granted mode.
 */
class RevokedCompatFallbackExecutor {
    suspend fun execute(
        operationName: String,
        requestedMode: AppOpMode,
        canAffectUid: Boolean,
        appOpsResult: AppOpModeChangeResult,
        applyFlag: suspend (permissionName: String) -> AppOpModeChangeResult?,
    ): RevokedCompatFallbackOutcome {
        val permissionName =
            AppOpRuntimePermissionCatalog.requiredPermission(operationName)
        val rejected = appOpsResult.isSafeRejection()
        val unchanged = RevokedCompatFallbackOutcome(
            result = appOpsResult,
            flagApplied = false,
            restrictionBlocked = rejected &&
                permissionName != null &&
                requestedMode == AppOpMode.IGNORE,
        )
        if (
            !rejected ||
            permissionName == null ||
            requestedMode !in FlagModes ||
            // The flag belongs to the whole UID, like a UID-scoped write.
            !canAffectUid
        ) {
            return unchanged
        }

        val flagResult = applyFlag(permissionName) ?: return unchanged
        return unchanged.copy(
            result = flagResult,
            flagApplied = flagResult is AppOpModeChangeResult.Success,
        )
    }

    private fun AppOpModeChangeResult.isSafeRejection(): Boolean =
        this is AppOpModeChangeResult.Failure &&
            phase in RejectionPhases &&
            restorationStatus == AppOpsRestorationStatus.SUCCEEDED

    private companion object {
        val FlagModes = setOf(
            AppOpMode.IGNORE,
            AppOpMode.ALLOW,
            AppOpMode.FOREGROUND,
        )
        val RejectionPhases = setOf(
            AppOpModeChangePhase.APPLY_REQUESTED,
            AppOpModeChangePhase.VERIFY_REQUESTED,
        )
    }
}
