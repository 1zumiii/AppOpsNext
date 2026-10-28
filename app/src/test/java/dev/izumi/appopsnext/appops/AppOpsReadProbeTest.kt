package dev.izumi.appopsnext.appops

import dev.izumi.appopsnext.appops.binder.BinderAppOpsReader
import dev.izumi.appopsnext.appops.binder.DeviceOp
import dev.izumi.appopsnext.appops.model.AppOpEntry
import dev.izumi.appopsnext.appops.model.PackageOpsSnapshot
import dev.izumi.appopsnext.appops.parser.OpStates
import dev.izumi.appopsnext.monitor.AppOpCodes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOpsReadProbeTest {
    private fun snapshot(vararg entries: AppOpEntry) = PackageOpsSnapshot("com.example", entries.toList(), "")

    private val platformTable = AppOpCodes.builtIn.map { (code, name) ->
        DeviceOp(code, name.removePrefix("android:").uppercase(), name)
    }

    @Test fun `package comparison ignores usage times but not modes or scopes`() {
        val text = snapshot(AppOpEntry("CAMERA", "ignore", "time=+5s ago", hasUidModePrefix = false))
        assertTrue(
            AppOpsReadComparison.samePackageOps(
                text,
                snapshot(AppOpEntry("CAMERA", "ignore", null, hasUidModePrefix = false)),
            ),
        )
        assertFalse(
            AppOpsReadComparison.samePackageOps(
                text,
                snapshot(AppOpEntry("CAMERA", "allow", null, hasUidModePrefix = false)),
            ),
        )
        assertFalse(
            AppOpsReadComparison.samePackageOps(
                text,
                snapshot(AppOpEntry("CAMERA", "ignore", null, hasUidModePrefix = true)),
            ),
        )
    }

    @Test fun `state comparison is made on the effective mode of every listed app`() {
        val text = OpStates(mapOf(10001 to "ignore"), emptyMap())
        val sameEffect = OpStates(mapOf(10001 to "ignore"), mapOf((10001 to "com.a") to "allow"))
        val different = OpStates(emptyMap(), mapOf((10001 to "com.a") to "ignore"))
        val apps = listOf(10001 to "com.a")
        assertTrue(AppOpsReadComparison.sameEffectiveStates(text, sameEffect, apps))
        assertFalse(AppOpsReadComparison.sameEffectiveStates(text, different, apps))
    }

    @Test fun `operation table is rejected only when it cannot be right`() {
        assertTrue(AppOpsReadComparison.opTableUsable(platformTable, AppOpCodes.builtIn))
        assertFalse(AppOpsReadComparison.opTableUsable(platformTable.take(10), AppOpCodes.builtIn))
        assertFalse(
            AppOpsReadComparison.opTableUsable(
                platformTable + platformTable.first().copy(code = 999),
                AppOpCodes.builtIn,
            ),
        )
        val renamed = platformTable.map { if (it.code == 26) it.copy(publicName = "android:other") else it }
        assertEquals(listOf(26), AppOpsReadComparison.opTableDifferences(renamed, AppOpCodes.builtIn))
    }

    @Test fun `a binder failure leaves every read on text and says why`() = runBlocking {
        val structured = structuredWithFailingBinder(platformTable)
        val probe = AppOpsReadProbe(
            structured = structured,
            textPackageOps = { _, _ -> snapshot() },
            textOpStates = { _, _ -> OpStates(emptyMap(), emptyMap()) },
            elapsedMillis = { 0L },
        )
        val result = probe.run(0, listOf(10001 to "com.a"), listOf(10001 to "com.a"))
        assertEquals(setOf(AppOpsReadCapability.OP_LIST), result.capabilities)
        assertTrue(result.notes.any { it.startsWith("packageOps=error(") })
        assertTrue(result.notes.any { it.startsWith("opStates=error(") })
    }

    @Test fun `without the platform table nothing is enabled`() = runBlocking {
        val probe = AppOpsReadProbe(
            structured = structuredWithFailingBinder(null),
            textPackageOps = { _, _ -> snapshot() },
            textOpStates = { _, _ -> OpStates(emptyMap(), emptyMap()) },
            elapsedMillis = { 0L },
        )
        val result = probe.run(0, emptyList(), emptyList())
        assertTrue(result.capabilities.isEmpty())
        assertEquals(listOf("opList=unavailable"), result.notes)
    }

    @Test fun `a failing binder read returns null, turns the capability off and reports it`() = runBlocking {
        val failures = mutableListOf<AppOpsReadCapability>()
        val structured = structuredWithFailingBinder(platformTable) { capability, _ -> failures += capability }
        structured.enable(setOf(AppOpsReadCapability.PACKAGE_OPS, AppOpsReadCapability.OP_LIST))
        try {
            assertNull(structured.packageOps("com.example", 10001))
            assertEquals(setOf(AppOpsReadCapability.OP_LIST), structured.enabled.value)
            assertEquals(listOf(AppOpsReadCapability.PACKAGE_OPS), failures)
            // Off now, so the next read goes straight to text without touching the binder.
            assertNull(structured.packageOps("com.example", 10001))
            assertEquals(1, failures.size)
        } finally {
            structured.enable(emptySet())
        }
    }

    private fun structuredWithFailingBinder(
        table: List<DeviceOp>?,
        onFailure: (AppOpsReadCapability, Throwable) -> Unit = { _, _ -> },
    ) = StructuredAppOpsReads(
        uidOf = { 10001 },
        reader = BinderAppOpsReader(serviceProvider = { error("binder unavailable") }),
        loadOpTable = { table },
        onFailure = onFailure,
    )
}
