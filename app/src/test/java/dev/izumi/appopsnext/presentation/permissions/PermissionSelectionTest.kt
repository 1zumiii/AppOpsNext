package dev.izumi.appopsnext.presentation.permissions

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.appops.parser.OpStates
import dev.izumi.appopsnext.apps.model.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionSelectionTest {
    @Test
    fun `batch includes selected apps from other searches and follows effective scopes`() {
        val targets = permissionBatchTargets(
            apps = listOf(app("a", 10001), app("b", 10002), app("c", 10003)),
            states = OpStates(
                uidModes = mapOf(10001 to "foreground"),
                packageModes = mapOf((10002 to "b") to "allow"),
            ),
            selectedPackages = setOf("a", "b"),
            operation = "CAMERA",
            requestedMode = AppOpMode.IGNORE,
        )
        assertEquals(listOf("a", "b"), targets.map { it.packageName })
        assertEquals(listOf(AppOpScope.UID, AppOpScope.PACKAGE), targets.map { it.preferredScope })
    }

    @Test
    fun `batch excludes unknown modes and packages no longer eligible`() {
        val targets = permissionBatchTargets(
            apps = listOf(app("unknown", 10001), app("default", 10002)),
            states = OpStates(uidModes = mapOf(10001 to "vendor"), packageModes = emptyMap()),
            selectedPackages = setOf("unknown", "default", "hidden.system"),
            operation = "CAMERA",
            requestedMode = AppOpMode.IGNORE,
        )
        assertEquals(listOf("default"), targets.map { it.packageName })
        assertEquals(AppOpScope.PACKAGE, targets.single().preferredScope)
    }

    private fun app(packageName: String, uid: Int) = InstalledApp(
        label = packageName,
        packageName = packageName,
        uid = uid,
        isSystemApp = false,
    )
}
