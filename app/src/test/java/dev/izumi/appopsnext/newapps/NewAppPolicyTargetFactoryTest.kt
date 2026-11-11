package dev.izumi.appopsnext.newapps

import dev.izumi.appopsnext.newapps.model.InstalledPackageFingerprint
import dev.izumi.appopsnext.newapps.model.InstalledPackageRecord
import dev.izumi.appopsnext.appops.changes.AppOpChangeRecord
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.templates.NewAppPolicyTemplate
import org.junit.Assert.assertEquals
import org.junit.Test

class NewAppPolicyTargetFactoryTest {
    @Test
    fun `target factory preserves built in rule order and app identity`() {
        val app = InstalledPackageRecord(
            fingerprint = InstalledPackageFingerprint(
                packageName = "com.example.newapp",
                firstInstallTimeMillis = 123L,
            ),
            label = "New app",
            uid = 10_123,
        )

        val targets = NewAppPolicyTargetFactory.create(
            app = app,
            template = NewAppPolicyTemplate.defaultTemplate,
        )

        assertEquals(
            NewAppPolicyTemplate.defaultTemplate.rules.map {
                it.stableOperationName
            },
            targets.map { it.stableOperationName },
        )
        assertEquals(
            setOf("com.example.newapp"),
            targets.map { it.packageName }.toSet(),
        )
        assertEquals(setOf("New app"), targets.map { it.appLabel }.toSet())
        assertEquals(setOf(10_123), targets.map { it.uid }.toSet())
        assertEquals(
            setOf(AppOpScope.PACKAGE),
            targets.map { it.preferredScope }.toSet(),
        )
    }

    @Test
    fun `restored settings keep their modes and are written at package scope`() {
        val app = InstalledPackageRecord(
            fingerprint = InstalledPackageFingerprint("com.example.again", 500L),
            label = "Again",
            uid = 10_200,
        )
        fun record(operation: String, mode: AppOpMode, scope: AppOpScope) = AppOpChangeRecord(
            userId = 0,
            packageName = "com.example.again",
            stableOperationName = operation,
            scope = scope,
            originalMode = AppOpMode.DEFAULT,
            recordedMode = mode,
            timeMillis = 100,
        )

        val targets = NewAppPolicyTargetFactory.restore(
            app,
            listOf(
                record("android:camera", AppOpMode.IGNORE, AppOpScope.PACKAGE),
                record("android:record_audio", AppOpMode.ALLOW, AppOpScope.UID),
            ),
        )

        assertEquals(
            listOf("android:camera" to AppOpMode.IGNORE, "android:record_audio" to AppOpMode.ALLOW),
            targets.map { it.stableOperationName to it.requestedMode },
        )
        assertEquals(setOf(AppOpScope.PACKAGE), targets.map { it.preferredScope }.toSet())
        assertEquals(setOf(10_200), targets.map { it.uid }.toSet())
    }
}
