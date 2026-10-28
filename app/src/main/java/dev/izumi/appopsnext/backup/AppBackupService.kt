package dev.izumi.appopsnext.backup

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
)

class AppBackupService(
    private val templateRepository: PermissionTemplateRepository,
    private val settingsRepository: UserSettingsRepository,
    private val monitorTargetsRepository: MonitorTargetsRepository,
    private val appVersion: String,
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
                ),
                monitorTargets = monitorTargetsRepository.targets.first(),
                monitorPoints = monitorTargetsRepository.pointSettings.first(),
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
        )
    }

    suspend fun import(backup: AppBackup) {
        backup.templates?.let { templateRepository.importTemplates(it) }
        backup.settings?.let { settingsRepository.applyBackup(it) }
        if (backup.monitorTargets != null || backup.monitorPoints != null) {
            monitorTargetsRepository.replace(backup.monitorTargets, backup.monitorPoints)
        }
    }
}
