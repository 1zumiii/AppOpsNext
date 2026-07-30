package dev.izumi.appopsnext.diagnostics

import dev.izumi.appopsnext.appops.model.AppOpsReadState
import dev.izumi.appopsnext.shizuku.model.PrivilegedServiceFailureReason
import dev.izumi.appopsnext.shizuku.model.PrivilegedServiceState
import dev.izumi.appopsnext.shizuku.model.ShizukuState
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticReportComposerTest {
    @Test
    fun `report contains environment states and events`() {
        val report = DiagnosticReportComposer.compose(
            environment = DiagnosticEnvironment(
                appVersionName = "1.1.2",
                appVersionCode = 19,
                buildType = "release",
                targetSdk = 35,
                compileSdk = 36,
                manufacturer = "Example",
                model = "Device",
                device = "device_code",
                androidVersion = "16",
                apiLevel = 36,
                securityPatch = "2026-07-01",
                buildId = "BUILD.1",
                userHandle = "UserHandle{0}",
                processUid = 10_123,
                locale = "en-US",
                supportedAbis = "arm64-v8a",
                shizukuApiVersion = "13.1.5",
                shizukuManagerVersion = "13.6.0 (1086)",
            ),
            shizukuState = ShizukuState.Ready(
                serverVersion = 13,
                serverUid = 2_000,
            ),
            privilegedServiceState = PrivilegedServiceState.Failure(
                PrivilegedServiceFailureReason.BIND_TIMED_OUT,
            ),
            appOpsReadState = AppOpsReadState.WaitingForBackend,
            eventLines = listOf("event-line"),
        )

        assertTrue(report.contains("version=1.1.2 (19)"))
        assertTrue(report.contains("android=16 (API 36)"))
        assertTrue(report.contains("ready(serverVersion=13,uid=2000)"))
        assertTrue(report.contains("failure(BIND_TIMED_OUT)"))
        assertTrue(report.contains("event-line"))
        assertTrue(report.contains("review and redact"))
    }
}
