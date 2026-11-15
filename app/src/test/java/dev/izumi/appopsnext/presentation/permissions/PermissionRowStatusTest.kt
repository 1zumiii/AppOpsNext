package dev.izumi.appopsnext.presentation.permissions

import dev.izumi.appopsnext.appops.changes.AppOpChangeRecord
import dev.izumi.appopsnext.appops.changes.AppOpChangeStatus
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.apps.AppSortOrder
import dev.izumi.appopsnext.apps.model.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PermissionRowStatusTest {
    private val app = InstalledApp(
        label = "Probe",
        packageName = "dev.izumi.probe",
        uid = 10221,
        isSystemApp = false,
        firstInstallTimeMillis = 1_000L,
        lastUpdateTimeMillis = 2_000L,
    )
    private val record = AppOpChangeRecord(
        userId = 0,
        packageName = app.packageName,
        stableOperationName = "android:camera",
        scope = AppOpScope.UID,
        originalMode = AppOpMode.ALLOW,
        recordedMode = AppOpMode.IGNORE,
        timeMillis = 3_000L,
    )
    private val modified = AppOpChangeStatus.Modified(record)
    private val elsewhere = AppOpChangeStatus.ChangedElsewhere(record, AppOpMode.ALLOW)

    private fun pick(
        order: AppSortOrder = AppSortOrder.NAME,
        status: AppOpChangeStatus? = null,
        mode: AppOpMode? = AppOpMode.ALLOW,
        runtimeGranted: Boolean? = null,
    ) = PermissionRowStatus.pick(app, order, record.timeMillis.takeIf { status != null }, status, mode, runtimeGranted)

    @Test fun `a change made elsewhere comes before everything else`() {
        assertEquals(
            PermissionRowStatus.ChangedElsewhere(elsewhere),
            pick(AppSortOrder.CHANGED_ONLY, elsewhere, AppOpMode.IGNORE, runtimeGranted = false),
        )
    }

    @Test fun `the time the list is sorted by comes next`() {
        assertEquals(PermissionRowStatus.SortTime(AppSortOrder.CHANGED_ONLY, 3_000L), pick(AppSortOrder.CHANGED_ONLY, modified))
        assertEquals(
            PermissionRowStatus.SortTime(AppSortOrder.INSTALL_TIME, 1_000L),
            pick(AppSortOrder.INSTALL_TIME, modified, AppOpMode.IGNORE, runtimeGranted = true),
        )
        assertEquals(PermissionRowStatus.SortTime(AppSortOrder.UPDATE_TIME, 2_000L), pick(AppSortOrder.UPDATE_TIME))
    }

    @Test fun `an ignored operation then shows its runtime permission`() {
        assertEquals(PermissionRowStatus.Runtime(false), pick(status = modified, mode = AppOpMode.IGNORE, runtimeGranted = false))
        assertEquals(PermissionRowStatus.Modified, pick(status = modified, mode = AppOpMode.ALLOW, runtimeGranted = false))
    }

    @Test fun `nothing to say leaves only the name`() {
        assertNull(pick())
        assertNull(pick(mode = AppOpMode.IGNORE, runtimeGranted = null))
    }
}
