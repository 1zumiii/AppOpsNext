package dev.izumi.appopsnext.newapps

import dev.izumi.appopsnext.appops.AdaptiveScopeModeChangeOutcome
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpModeChangePhase
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.appops.model.AppOpsRestorationStatus
import dev.izumi.appopsnext.batch.model.BatchOperationTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NewAppRuleLogTest {
    private val target = BatchOperationTarget(
        packageName = "com.example",
        appLabel = "Example",
        uid = 10_123,
        stableOperationName = "CAMERA",
        preferredScope = AppOpScope.PACKAGE,
        requestedMode = AppOpMode.IGNORE,
    )
    private val success = AdaptiveScopeModeChangeOutcome(
        result = AppOpModeChangeResult.Success(AppOpMode.ALLOW, AppOpMode.IGNORE),
        appliedScope = AppOpScope.PACKAGE,
        fallbackAttempted = false,
    )
    private val failure = AdaptiveScopeModeChangeOutcome(
        result = AppOpModeChangeResult.Failure(
            phase = AppOpModeChangePhase.VERIFY_REQUESTED,
            originalMode = AppOpMode.ALLOW,
            observedMode = AppOpMode.ALLOW,
            restorationStatus = AppOpsRestorationStatus.NOT_REQUIRED,
        ),
        appliedScope = AppOpScope.UID,
        fallbackAttempted = true,
    )

    @Test fun `a rule that worked has no line of its own`() {
        assertNull(NewAppRuleLog.failureLine(target, success))
        assertEquals("CAMERA:IGNORE@PACKAGE", NewAppRuleLog.appliedEntry(target, success))
    }

    @Test fun `a fallback or the permission flag is named in the summary entry`() {
        val viaFlag = success.copy(appliedScope = AppOpScope.UID, fallbackAttempted = true, permissionFlagApplied = true)
        assertEquals("CAMERA:IGNORE@UID+fallback+permissionFlag", NewAppRuleLog.appliedEntry(target, viaFlag))
    }

    @Test fun `a rule that failed keeps its full line and stays out of the summary`() {
        assertNull(NewAppRuleLog.appliedEntry(target, failure))
        assertEquals(
            "New-app rule completed. package=com.example, operation=CAMERA, preferredScope=PACKAGE, " +
                "resolvedScope=UID, fallback=true, permissionFlag=false, " +
                "result=failure:VERIFY_REQUESTED:NOT_REQUIRED",
            NewAppRuleLog.failureLine(target, failure),
        )
    }

    @Test fun `the summary lists the rules that worked`() {
        assertEquals(
            "New-app policy progress. package=com.example, restored=false, success=2, failure=1, " +
                "complete=true, applied=[CAMERA:IGNORE@PACKAGE, RECORD_AUDIO:IGNORE@UID]",
            NewAppRuleLog.progressLine(
                "com.example", false, 2, 1, true,
                listOf("CAMERA:IGNORE@PACKAGE", "RECORD_AUDIO:IGNORE@UID"),
            ),
        )
        assertEquals(
            "New-app policy progress. package=com.example, restored=true, success=0, failure=0, complete=false",
            NewAppRuleLog.progressLine("com.example", true, 0, 0, false, emptyList()),
        )
    }
}
