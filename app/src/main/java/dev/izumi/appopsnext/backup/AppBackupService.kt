package dev.izumi.appopsnext.backup

import dev.izumi.appopsnext.guard.GuardRepository
import dev.izumi.appopsnext.monitor.MonitorTargetsRepository
import dev.izumi.appopsnext.settings.UserSettingsRepository
import dev.izumi.appopsnext.templates.PermissionTemplateRepository
import java.util.UUID
import kotlinx.coroutines.flow.first

/** What importing a backup would change, shown before anything is written. */
data class BackupPreview(
    val backup: AppBackup,
    /** Null when the backup has no templates section. */
    val templates: TemplateMergeResult?,
    val monitorAppCount: Int?,
    val monitorPointCount: Int?,
    /** Null when the backup has no guard section. */
    val guard: GuardImportPlan?,
)

/** Guard rules whose app is installed here, with this device's UIDs. */
data class GuardImportPlan(
    val backup: GuardBackup,
    /** Rules left out because their app is not installed on this device. */
    val skipped: Int,
)

class AppBackupService(
    private val templateRepository: PermissionTemplateRepository,
    private val settingsRepository: UserSettingsRepository,
    private val monitorTargetsRepository: MonitorTargetsRepository,
    private val guardRepository: GuardRepository,
    private val appVersion: String,
    /** This device's UID of a package, or null when it is not installed. */
    private val packageUid: (String) -> Int?,
) {
    suspend fun export(nowMillis: Long): String {
        val settings = settingsRepository.settings.first()
        return AppBackupCodec.encode(
            AppBackup(
                appVersion = appVersion,
                createdMillis = nowMillis,
                templates = templateRepository.templates.first(),
                settings = UserSettingsBackup(
                    hideSystemApps = settings.hideSystemApps,
                    suppressDenyFallbackNotice = settings.suppressDenyFallbackNotice,
                    suppressPermissionFlagNotice = settings.suppressPermissionFlagNotice,
                    autoApplyNewAppTemplate = settings.autoApplyNewAppTemplate,
                    monitorHeadsUp = settings.monitorHeadsUp,
                    suppressBatteryNotice = settings.suppressBatteryNotice,
                    showAllMonitorOperations = settings.showAllMonitorOperations,
                    suppressAllOperationsWarning = settings.suppressAllOperationsWarning,
                    saveIndividualHistory = settings.saveIndividualHistory,
                    savedHistoryOperations = settings.savedHistoryOperations,
                    appSortOrder = settings.appSortOrder,
                ),
                monitorTargets = monitorTargetsRepository.targets.first(),
                monitorPoints = monitorTargetsRepository.pointSettings.first(),
                guard = GuardBackup(
                    mode = guardRepository.mode.first(),
                    rules = guardRepository.rules.first(),
                ),
            ),
        )
    }

    /**
     * Reads the whole file before anything is written, so a file that fails here
     * leaves the app untouched.
     *
     * @throws BackupFormatException when [text] cannot be imported.
     */
    suspend fun preview(text: String): BackupPreview {
        val backup = AppBackupCodec.decode(text)
        return BackupPreview(
            backup = backup,
            templates = backup.templates?.let { imported ->
                TemplateImportMerge.merge(templateRepository.templates.first(), imported) {
                    UUID.randomUUID().toString()
                }
            },
            monitorAppCount = backup.monitorTargets?.size,
            monitorPointCount = backup.monitorPoints?.size,
            guard = backup.guard?.let { guard ->
                val rules = guard.rules.mapNotNull { rule ->
                    packageUid(rule.packageName)?.let { uid -> rule.copy(uid = uid) }
                }
                GuardImportPlan(guard.copy(rules = rules), skipped = guard.rules.size - rules.size)
            },
        )
    }

    /** [guard] is the preview's plan, which already holds this device's UIDs. */
    suspend fun import(backup: AppBackup, guard: GuardImportPlan?) {
        backup.templates?.let { templateRepository.importTemplates(it) }
        backup.settings?.let { settingsRepository.applyBackup(it) }
        if (backup.monitorTargets != null || backup.monitorPoints != null) {
            monitorTargetsRepository.replace(backup.monitorTargets, backup.monitorPoints)
        }
        guard?.let { guardRepository.import(it.backup.mode, it.backup.rules) }
    }
}
