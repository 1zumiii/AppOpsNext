package dev.izumi.appopsnext.backup

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.apps.AppSortOrder
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.guard.GuardMode
import dev.izumi.appopsnext.guard.GuardRule
import dev.izumi.appopsnext.monitor.MonitorOutcomes
import dev.izumi.appopsnext.monitor.MonitorPointSettings
import dev.izumi.appopsnext.monitor.MonitorTarget
import dev.izumi.appopsnext.templates.NewAppPolicyTemplate
import dev.izumi.appopsnext.templates.model.PermissionTemplate
import dev.izumi.appopsnext.templates.model.PermissionTemplateRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class AppBackupCodecTest {
    private val backup = AppBackup(
        appVersion = "1.6.6",
        createdMillis = 1_730_000_000_000,
        templates = listOf(
            NewAppPolicyTemplate.defaultTemplate,
            PermissionTemplate(
                id = "privacy",
                name = "隐私 / Privacy",
                rules = listOf(
                    PermissionTemplateRule("android:camera", AppOpMode.IGNORE, AppOpScope.UID),
                ),
            ),
        ),
        settings = UserSettingsBackup(
            hideSystemApps = false,
            autoApplyNewAppTemplate = true,
            savedHistoryOperations = setOf("android:camera", "android:coarse_location"),
            appSortOrder = AppSortOrder.INSTALL_TIME,
        ),
        monitorTargets = listOf(MonitorTarget("dev.izumi.one", setOf("android:camera"))),
        monitorPoints = listOf(
            MonitorPointSettings(
                packageName = "dev.izumi.one",
                operationName = "android:camera",
                throttleSeconds = 60,
                outcomes = MonitorOutcomes.REFUSED,
            ),
        ),
        guard = GuardBackup(
            mode = GuardMode.PERIODIC,
            rules = listOf(
                GuardRule("dev.izumi.one", 10_123, "android:camera", AppOpScope.PACKAGE, AppOpMode.IGNORE, true),
                GuardRule("dev.izumi.two", 10_124, "android:coarse_location", AppOpScope.UID, AppOpMode.DENY, false),
            ),
        ),
    )

    @Test fun `round trips every section`() {
        assertEquals(backup, AppBackupCodec.decode(AppBackupCodec.encode(backup)))
    }

    @Test fun `a missing section stays null so importing leaves that part alone`() {
        val decoded = AppBackupCodec.decode(
            AppBackupCodec.encode(AppBackup(monitorTargets = backup.monitorTargets)),
        )
        assertNull(decoded.templates)
        assertNull(decoded.settings)
        assertNull(decoded.monitorPoints)
        assertEquals(backup.monitorTargets, decoded.monitorTargets)
    }

    @Test fun `a guard section without a mode leaves the mode alone`() {
        val rules = backup.guard!!.rules
        val decoded = AppBackupCodec.decode(AppBackupCodec.encode(AppBackup(guard = GuardBackup(null, rules))))
        assertEquals(GuardBackup(null, rules), decoded.guard)
    }

    @Test fun `unknown sections and newer section versions are skipped`() {
        val text = """
            APP_OPS_NEXT_BACKUP 1
            @section guard_rules 1
            dev.izumi.one android:camera ignore
            @end
            @section settings 2
            hide_system_apps=false
            @end
            @section monitor_targets 1
            dev.izumi.one android:camera
            @end
        """.trimIndent()
        val decoded = AppBackupCodec.decode(text)
        assertEquals(listOf("guard_rules", "settings"), decoded.skippedSections)
        assertNull(decoded.settings)
        assertEquals(backup.monitorTargets, decoded.monitorTargets)
    }

    @Test fun `unknown settings keys and unreadable values are ignored`() {
        val text = """
            APP_OPS_NEXT_BACKUP 1
            @section settings 1
            hide_system_apps=maybe
            future_setting=true
            monitor_heads_up=true
            @end
        """.trimIndent()
        assertEquals(UserSettingsBackup(monitorHeadsUp = true), AppBackupCodec.decode(text).settings)
    }

    @Test fun `body lines starting with the marker survive`() {
        val text = BackupContainer.encode(listOf(BackupSection("future", 1, listOf("@end", "@@x"))))
        assertEquals(listOf("@end", "@@x"), BackupContainer.decode(text).single().lines)
    }

    @Test fun `windows line endings and a byte order mark are accepted`() {
        val text = Char(0xFEFF) + AppBackupCodec.encode(backup).replace("\n", "\r\n")
        assertEquals(backup, AppBackupCodec.decode(text))
    }

    @Test fun `a foreign file is rejected`() {
        assertProblem(BackupProblem.FOREIGN, "")
        assertProblem(BackupProblem.FOREIGN, "hello world")
        // A bare template export is not a backup.
        assertProblem(BackupProblem.FOREIGN, "APP_OPS_NEXT_TEMPLATES_V1\nT\tYQ\tYQ")
    }

    @Test fun `a newer container format is rejected`() {
        assertProblem(BackupProblem.NEWER_FORMAT, "APP_OPS_NEXT_BACKUP 2\n")
    }

    @Test fun `broken structure is rejected as corrupt`() {
        assertProblem(BackupProblem.CORRUPT, "APP_OPS_NEXT_BACKUP x")
        assertProblem(BackupProblem.CORRUPT, "APP_OPS_NEXT_BACKUP 1\n@section settings 1\nhide_system_apps=true")
        assertProblem(BackupProblem.CORRUPT, "APP_OPS_NEXT_BACKUP 1\nstray line")
        assertProblem(BackupProblem.CORRUPT, "APP_OPS_NEXT_BACKUP 1\n@section settings\n@end")
        assertProblem(
            BackupProblem.CORRUPT,
            "APP_OPS_NEXT_BACKUP 1\n@section settings 1\n@section templates 1\n@end",
        )
        assertProblem(
            BackupProblem.CORRUPT,
            "APP_OPS_NEXT_BACKUP 1\n@section settings 1\n@end\n@section settings 1\n@end",
        )
        assertProblem(BackupProblem.CORRUPT, "APP_OPS_NEXT_BACKUP 1\n@section templates 1\nnot templates\n@end")
    }

    @Test fun `a cut off file is rejected rather than half imported`() {
        val text = AppBackupCodec.encode(backup)
        assertProblem(BackupProblem.CORRUPT, text.substring(0, text.lastIndexOf("@end")))
    }

    @Test fun `a backup with nothing importable is rejected`() {
        assertProblem(BackupProblem.NOTHING_TO_IMPORT, "APP_OPS_NEXT_BACKUP 1\n")
        assertProblem(
            BackupProblem.NOTHING_TO_IMPORT,
            "APP_OPS_NEXT_BACKUP 1\n@section about 1\napp=1.6.6\n@end\n@section guard_rules 1\n@end",
        )
    }

    private fun assertProblem(expected: BackupProblem, text: String) {
        try {
            AppBackupCodec.decode(text)
            fail("Expected $expected")
        } catch (error: BackupFormatException) {
            assertEquals(expected, error.problem)
        }
    }
}
