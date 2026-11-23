package dev.izumi.appopsnext.temporary

import dev.izumi.appopsnext.appops.AdaptiveScopeModeChangeExecutor
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpModeChangePhase
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.appops.model.AppOpsRestorationStatus
import dev.izumi.appopsnext.guard.GuardWriteTarget

/** Runs under the device write queue. Each attempted scope needs its own recovery record. */
internal class TemporaryAllowWriter(
    packagesForUid: (Int) -> List<String>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val adaptiveScope = AdaptiveScopeModeChangeExecutor(packagesForUid = packagesForUid)

    suspend fun allow(
        target: GuardWriteTarget,
        preferredScope: AppOpScope,
        shownOriginal: AppOpMode,
        readMode: suspend (AppOpScope) -> AppOpMode?,
        stage: suspend (TemporaryAllow) -> Boolean,
        clear: suspend (TemporaryAllow) -> Unit,
        save: suspend (TemporaryAllow) -> Unit,
        changeMode: suspend (AppOpScope, AppOpMode, Boolean) -> AppOpModeChangeResult,
    ): TemporaryAllowResult {
        val original = readMode(preferredScope)
        if (original != shownOriginal || original == AppOpMode.ALLOW) {
            return TemporaryAllowResult.Write(originalFailure(original), preferredScope)
        }
        val openBy = clock() + TemporaryAllow.OPEN_WINDOW_MILLIS
        var stagingFailed = false
        val outcome = adaptiveScope.execute(
            packageName = target.packageName,
            uid = target.uid,
            preferredScope = preferredScope,
            requestedMode = AppOpMode.ALLOW,
            readMode = readMode,
        ) { scope ->
            val originalInScope = if (scope == preferredScope) original else readMode(scope)
            if (originalInScope == null) return@execute originalFailure(null)
            if (originalInScope == AppOpMode.ALLOW) {
                return@execute AppOpModeChangeResult.Success(originalInScope, originalInScope)
            }
            // Android can fold a UID Allow into the absence of a UID override.
            // Journal both possible readbacks until the write has been verified.
            val acceptUidDefault = scope == AppOpScope.UID && originalInScope != AppOpMode.DEFAULT &&
                readMode(AppOpScope.PACKAGE) == AppOpMode.ALLOW
            val entry = TemporaryAllow(
                target, scope, originalInScope, openBy, displayedOriginalMode = shownOriginal,
                awaitingUidReadback = acceptUidDefault,
            )
            if (!stage(entry)) {
                stagingFailed = true
                return@execute originalFailure(originalInScope)
            }
            // Scope fallback is possible only after this attempt has been rolled back.
            changeMode(scope, originalInScope, acceptUidDefault).also { result ->
                when (result) {
                    is AppOpModeChangeResult.Failure -> {
                        if (result.restorationStatus == AppOpsRestorationStatus.FAILED) {
                            save(entry.restoring())
                        } else {
                            clear(entry)
                        }
                    }
                    is AppOpModeChangeResult.Success -> when {
                        result.originalMode == result.appliedMode -> clear(entry)
                        acceptUidDefault -> save(entry.copy(appliedMode = result.appliedMode, awaitingUidReadback = false))
                    }
                }
            }
        }
        if (stagingFailed) return TemporaryAllowResult.Unavailable.MONITOR_UNAVAILABLE
        val result = outcome.result
        if (result is AppOpModeChangeResult.Success && result.originalMode == result.appliedMode) {
            return TemporaryAllowResult.Write(originalFailure(result.appliedMode), outcome.appliedScope)
        }
        return TemporaryAllowResult.Write(result, outcome.appliedScope)
    }

    private fun originalFailure(observed: AppOpMode?) = AppOpModeChangeResult.Failure(
        phase = if (observed == null) AppOpModeChangePhase.READ_ORIGINAL else AppOpModeChangePhase.CHECK_ORIGINAL,
        originalMode = observed,
        observedMode = observed,
        restorationStatus = AppOpsRestorationStatus.NOT_REQUIRED,
    )
}
