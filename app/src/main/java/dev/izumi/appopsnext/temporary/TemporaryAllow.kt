package dev.izumi.appopsnext.temporary

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.guard.GuardWriteTarget

internal data class TemporaryAllow(
    val target: GuardWriteTarget,
    val scope: AppOpScope,
    val originalMode: AppOpMode,
    val openByMillis: Long,
    val phase: Phase = Phase.WAITING_TO_OPEN,
    val offScreenSinceMillis: Long? = null,
    val displayedOriginalMode: AppOpMode = originalMode,
    val appliedMode: AppOpMode = AppOpMode.ALLOW,
    val awaitingUidReadback: Boolean = false,
) {
    enum class Phase { WAITING_TO_OPEN, IN_USE, RESTORING }

    fun advance(onScreen: Boolean?, now: Long): TemporaryAllow = when (phase) {
        Phase.WAITING_TO_OPEN -> when {
            now >= openByMillis || now < openByMillis - OPEN_WINDOW_MILLIS -> restoring()
            onScreen == true -> copy(phase = Phase.IN_USE)
            else -> this
        }
        Phase.IN_USE -> {
            if (onScreen == false) {
                val since = offScreenSinceMillis?.takeIf { it <= now } ?: now
                if (now - since >= LEAVE_GRACE_MILLIS) restoring() else copy(offScreenSinceMillis = since)
            } else {
                copy(offScreenSinceMillis = null)
            }
        }
        Phase.RESTORING -> this
    }

    fun restoring(): TemporaryAllow = copy(phase = Phase.RESTORING, offScreenSinceMillis = null)

    /** Returns true when the record can be removed. The caller holds the device write queue. */
    suspend fun restoreIfStillAllowed(
        observed: AppOpMode?,
        readPackageMode: suspend () -> AppOpMode? = { null },
        restore: suspend (AppOpScope, AppOpMode) -> Boolean,
    ): Boolean {
        if (observed == null) return false
        if (observed != appliedMode && !(awaitingUidReadback && observed == AppOpMode.DEFAULT)) return true
        // A cleared UID override only allows access while the package still allows it.
        if (observed == AppOpMode.DEFAULT) {
            val packageMode = readPackageMode() ?: return false
            if (packageMode != AppOpMode.ALLOW) return true
        }
        return restore(scope, originalMode)
    }

    companion object {
        const val OPEN_WINDOW_MILLIS = 120_000L
        const val LEAVE_GRACE_MILLIS = 15_000L
    }
}
