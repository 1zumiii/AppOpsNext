package dev.izumi.appopsnext.newapps

import dev.izumi.appopsnext.newapps.model.InstalledPackageFingerprint
import dev.izumi.appopsnext.newapps.model.InstalledPackageRecord
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
    }
}
