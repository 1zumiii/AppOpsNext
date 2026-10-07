package dev.izumi.appopsnext.presentation.batch

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.batch.model.BatchOperationTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class SharedUidPackagesTest {
    private val packagesByUid = mapOf(
        10_001 to listOf("example.solo"),
        10_002 to listOf("example.shared.a", "example.shared.b", "example.shared.c"),
    )

    @Test
    fun `uid writes on unshared uids list nothing`() {
        assertEquals(
            emptyList<String>(),
            affected(target("example.solo", 10_001, AppOpScope.UID)),
        )
    }

    @Test
    fun `shared uid lists only the siblings outside the selection`() {
        assertEquals(
            listOf("example.shared.c"),
            affected(
                target("example.shared.a", 10_002, AppOpScope.UID),
                target("example.shared.b", 10_002, AppOpScope.UID),
            ),
        )
    }

    @Test
    fun `package writes never reach siblings`() {
        assertEquals(
            emptyList<String>(),
            affected(target("example.shared.a", 10_002, AppOpScope.PACKAGE)),
        )
    }

    private fun affected(vararg targets: BatchOperationTarget) =
        unselectedSharedUidPackages(targets.toList()) { packagesByUid[it].orEmpty() }

    private fun target(packageName: String, uid: Int, scope: AppOpScope) =
        BatchOperationTarget(
            packageName = packageName,
            appLabel = packageName,
            uid = uid,
            stableOperationName = "android:camera",
            preferredScope = scope,
            requestedMode = AppOpMode.IGNORE,
        )
}
