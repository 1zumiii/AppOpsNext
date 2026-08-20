package dev.izumi.appopsnext.presentation.app_detail

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpModeChangePhase
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.appops.model.AppOpsRestorationStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModeChangeAlternativePolicyTest {
    @Test
    fun `safely rejected allow can offer foreground`() {
        assertTrue(
            ModeChangeAlternativePolicy.canTryForeground(
                request = request(
                    originalMode = AppOpMode.IGNORE,
                    requestedMode = AppOpMode.ALLOW,
                ),
                result = rejectedMode(),
            ),
        )
    }

    @Test
    fun `deny never offers foreground alternative`() {
        assertFalse(
            ModeChangeAlternativePolicy.canTryForeground(
                request = request(
                    originalMode = AppOpMode.IGNORE,
                    requestedMode = AppOpMode.DENY,
                ),
                result = rejectedMode(),
            ),
        )
    }

    @Test
    fun `foreground original mode is not offered again`() {
        assertFalse(
            ModeChangeAlternativePolicy.canTryForeground(
                request = request(
                    originalMode = AppOpMode.FOREGROUND,
                    requestedMode = AppOpMode.ALLOW,
                ),
                result = rejectedMode(),
            ),
        )
    }

    @Test
    fun `denied runtime permission does not offer foreground`() {
        assertFalse(
            ModeChangeAlternativePolicy.canTryForeground(
                request = request(
                    originalMode = AppOpMode.IGNORE,
                    requestedMode = AppOpMode.ALLOW,
                    runtimePermissionDenied = true,
                ),
                result = rejectedMode(),
            ),
        )
    }

    @Test
    fun `unsafe failed restoration never offers foreground`() {
        assertFalse(
            ModeChangeAlternativePolicy.canTryForeground(
                request = request(
                    originalMode = AppOpMode.IGNORE,
                    requestedMode = AppOpMode.ALLOW,
                ),
                result = rejectedMode(
                    phase = AppOpModeChangePhase.RESTORE_ORIGINAL,
                    restorationStatus = AppOpsRestorationStatus.FAILED,
                ),
            ),
        )
    }

    private fun request(
        originalMode: AppOpMode,
        requestedMode: AppOpMode,
        runtimePermissionDenied: Boolean = false,
    ) = AppOpModeChangeRequest(
        packageName = "dev.example.target",
        operationName = "android:camera",
        scope = AppOpScope.UID,
        originalMode = originalMode,
        requestedMode = requestedMode,
        affectedPackages = listOf("dev.example.target"),
        runtimePermissionDenied = runtimePermissionDenied,
    )

    private fun rejectedMode(
        phase: AppOpModeChangePhase =
            AppOpModeChangePhase.VERIFY_REQUESTED,
        restorationStatus: AppOpsRestorationStatus =
            AppOpsRestorationStatus.SUCCEEDED,
    ) = AppOpModeChangeResult.Failure(
        phase = phase,
        originalMode = AppOpMode.IGNORE,
        observedMode = AppOpMode.DEFAULT,
        restorationStatus = restorationStatus,
    )
}
