package dev.izumi.appopsnext.newapps

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpModeChangePhase
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.appops.model.AppOpsRestorationStatus
import dev.izumi.appopsnext.batch.model.BatchOperationItemResult
import dev.izumi.appopsnext.batch.model.BatchOperationTarget
import dev.izumi.appopsnext.newapps.model.InstalledPackageFingerprint
import org.junit.Assert.assertEquals
import org.junit.Test

class NewAppResultTest {
    @Test
    fun `rules are grouped by installation with the latest first`() {
        val older = InstalledPackageFingerprint("com.example.older", 1_000)
        val newer = InstalledPackageFingerprint("com.example.newer", 2_000)

        val results = NewAppResult.group(
            listOf(
                progress(older, "android:camera", success = true),
                progress(newer, "android:record_audio", success = false),
                progress(newer, "android:camera", success = true),
            ),
        )

        assertEquals(listOf(newer, older), results.map { it.installation })
        assertEquals(
            listOf("android:camera", "android:record_audio"),
            results.first().items.map { it.target.stableOperationName },
        )
        assertEquals(1, results.first().successCount)
        assertEquals(1, results.first().failureCount)
        assertEquals("com.example.newer label", results.first().appLabel)
    }

    @Test
    fun `an installation of the same package at another time is its own result`() {
        val first = InstalledPackageFingerprint("com.example.app", 1_000)
        val again = InstalledPackageFingerprint("com.example.app", 3_000)

        val results = NewAppResult.group(
            listOf(
                progress(first, "android:camera", success = false),
                progress(again, "android:camera", success = true),
            ),
        )

        assertEquals(listOf(again, first), results.map { it.installation })
        assertEquals(listOf(0, 1), results.map { it.failureCount })
    }

    private fun progress(
        installation: InstalledPackageFingerprint,
        operation: String,
        success: Boolean,
    ) = NewAppRuleProgress(
        installation = installation,
        item = BatchOperationItemResult(
            target = BatchOperationTarget(
                packageName = installation.packageName,
                appLabel = "${installation.packageName} label",
                uid = 10_100,
                stableOperationName = operation,
                preferredScope = AppOpScope.PACKAGE,
                requestedMode = AppOpMode.IGNORE,
            ),
            result = if (success) {
                AppOpModeChangeResult.Success(AppOpMode.ALLOW, AppOpMode.IGNORE)
            } else {
                AppOpModeChangeResult.Failure(
                    phase = AppOpModeChangePhase.VERIFY_REQUESTED,
                    originalMode = AppOpMode.ALLOW,
                    observedMode = AppOpMode.ALLOW,
                    restorationStatus = AppOpsRestorationStatus.SUCCEEDED,
                )
            },
        ),
    )
}
