package dev.izumi.appopsnext.backup

import dev.izumi.appopsnext.guard.GuardCodec
import dev.izumi.appopsnext.guard.GuardMode
import dev.izumi.appopsnext.guard.GuardRule
import dev.izumi.appopsnext.monitor.MonitorPointSettings
import dev.izumi.appopsnext.monitor.MonitorPointSettingsCodec
import dev.izumi.appopsnext.monitor.MonitorTarget
import dev.izumi.appopsnext.monitor.MonitorTargetsCodec
import dev.izumi.appopsnext.templates.PermissionTemplateCodec
import dev.izumi.appopsnext.templates.model.PermissionTemplate

/**
 * Preferences carried by a backup. Null leaves the current value alone, which is
 * how a backup from a build that did not know a setting yet is read.
 *
 * Background monitoring is left out on purpose: switching it on goes through a
 * confirmation and a battery check on the device itself.
 */
data class UserSettingsBackup(
    val hideSystemApps: Boolean? = null,
    val suppressDenyFallbackNotice: Boolean? = null,
    val suppressPermissionFlagNotice: Boolean? = null,
    val autoApplyNewAppTemplate: Boolean? = null,
    val monitorHeadsUp: Boolean? = null,
    val suppressBatteryNotice: Boolean? = null,
    val showAllMonitorOperations: Boolean? = null,
    val suppressAllOperationsWarning: Boolean? = null,
    val saveIndividualHistory: Boolean? = null,
    val savedHistoryOperations: Set<String>? = null,
)

/**
 * What a backup file holds. A null section was not in the file, and importing
 * leaves that part of the app as it is. The saved history archive is never part
 * of a backup: it can be large and records what was used when.
 */
data class AppBackup(
    val appVersion: String? = null,
    val createdMillis: Long? = null,
    val templates: List<PermissionTemplate>? = null,
    val settings: UserSettingsBackup? = null,
    val monitorTargets: List<MonitorTarget>? = null,
    val monitorPoints: List<MonitorPointSettings>? = null,
    val guard: GuardBackup? = null,
    /** Sections passed over because this build does not know them or their version. */
    val skippedSections: List<String> = emptyList(),
) {
    val hasContent: Boolean
        get() = templates != null || settings != null ||
            monitorTargets != null || monitorPoints != null || guard != null
}

/**
 * Guarded settings and how they are checked. Rules keep the UID of the device
 * that exported them, so an import looks each package up again. What the guard
 * saw and restored stays on the device, like the history.
 */
data class GuardBackup(
    val mode: GuardMode?,
    val rules: List<GuardRule>,
)

object AppBackupCodec {
    private const val ABOUT = "about"
    private const val TEMPLATES = "templates"
    private const val SETTINGS = "settings"
    private const val MONITOR_TARGETS = "monitor_targets"
    private const val MONITOR_POINTS = "monitor_points"
    private const val GUARD = "guard"
    private const val VERSION = 1

    private const val APP = "app"
    private const val CREATED = "created"
    private const val GUARD_MODE = "mode"

    fun encode(backup: AppBackup): String =
        BackupContainer.encode(
            buildList {
                val about = buildList {
                    backup.appVersion?.let { add("$APP=$it") }
                    backup.createdMillis?.let { add("$CREATED=$it") }
                }
                add(BackupSection(ABOUT, VERSION, about))
                backup.templates?.let {
                    add(BackupSection(TEMPLATES, VERSION, PermissionTemplateCodec.encode(it).bodyLines()))
                }
                backup.settings?.let {
                    add(BackupSection(SETTINGS, VERSION, SettingsLines.encode(it)))
                }
                backup.monitorTargets?.let {
                    add(BackupSection(MONITOR_TARGETS, VERSION, MonitorTargetsCodec.encode(it).bodyLines()))
                }
                backup.monitorPoints?.let {
                    add(BackupSection(MONITOR_POINTS, VERSION, MonitorPointSettingsCodec.encode(it).bodyLines()))
                }
                backup.guard?.let { guard ->
                    val lines = listOfNotNull(guard.mode?.let { "$GUARD_MODE=${it.name}" }) +
                        guard.rules.map(GuardCodec::encodeRule)
                    add(BackupSection(GUARD, VERSION, lines))
                }
            },
        )

    /** @throws BackupFormatException when nothing can be imported from [text]. */
    fun decode(text: String): AppBackup {
        var backup = AppBackup()
        val skipped = mutableListOf<String>()
        BackupContainer.decode(text).forEach { section ->
            if (section.version > VERSION) {
                skipped += section.name
                return@forEach
            }
            backup = when (section.name) {
                ABOUT -> {
                    val fields = section.lines.keyValues()
                    backup.copy(
                        appVersion = fields[APP]?.takeIf(String::isNotBlank),
                        createdMillis = fields[CREATED]?.toLongOrNull(),
                    )
                }
                TEMPLATES -> backup.copy(templates = decodeTemplates(section.lines))
                SETTINGS -> backup.copy(settings = SettingsLines.decode(section.lines))
                MONITOR_TARGETS -> backup.copy(
                    monitorTargets = MonitorTargetsCodec.decode(section.lines.joinToString("\n")),
                )
                MONITOR_POINTS -> backup.copy(
                    monitorPoints = MonitorPointSettingsCodec.decode(section.lines.joinToString("\n")),
                )
                GUARD -> backup.copy(guard = decodeGuard(section.lines))
                else -> {
                    skipped += section.name
                    backup
                }
            }
        }
        backup = backup.copy(skippedSections = skipped)
        if (!backup.hasContent) throw BackupFormatException(BackupProblem.NOTHING_TO_IMPORT)
        return backup
    }

    /** The template codec drops bad records quietly, so a missing header is the one thing to reject. */
    private fun decodeTemplates(lines: List<String>): List<PermissionTemplate> {
        if (lines.firstOrNull() != PermissionTemplateCodec.HEADER) {
            throw BackupFormatException(BackupProblem.CORRUPT)
        }
        return PermissionTemplateCodec.decode(lines.joinToString("\n"))
    }

    /** A rule record never holds `=`, so the mode line cannot be taken for one. */
    private fun decodeGuard(lines: List<String>): GuardBackup {
        val modeName = lines.keyValues()[GUARD_MODE]
        return GuardBackup(
            mode = GuardMode.entries.firstOrNull { it.name == modeName },
            rules = lines.filterNot { '=' in it }.mapNotNull(GuardCodec::decodeRule),
        )
    }

    private fun String.bodyLines(): List<String> = lines().filter(String::isNotEmpty)
}

private fun List<String>.keyValues(): Map<String, String> =
    filter { '=' in it }.associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }

/**
 * Settings as `key=value` lines, named after their DataStore keys. A key this
 * build does not know, or a value it cannot read, is skipped rather than failing
 * the whole section.
 */
private object SettingsLines {
    private const val HIDE_SYSTEM_APPS = "hide_system_apps"
    private const val SUPPRESS_DENY_FALLBACK_NOTICE = "suppress_deny_fallback_notice"
    private const val SUPPRESS_PERMISSION_FLAG_NOTICE = "suppress_permission_flag_notice"
    private const val AUTO_APPLY_NEW_APP_TEMPLATE = "auto_apply_new_app_template"
    private const val MONITOR_HEADS_UP = "monitor_heads_up"
    private const val SUPPRESS_BATTERY_NOTICE = "suppress_battery_notice"
    private const val SHOW_ALL_MONITOR_OPERATIONS = "show_all_monitor_operations"
    private const val SUPPRESS_ALL_OPERATIONS_WARNING = "suppress_all_operations_warning_noise_rom"
    private const val SAVE_INDIVIDUAL_HISTORY = "save_individual_history"
    private const val SAVED_HISTORY_OPERATIONS = "saved_history_operations"

    fun encode(settings: UserSettingsBackup): List<String> =
        buildList {
            fun put(key: String, value: Boolean?) {
                value?.let { add("$key=$it") }
            }
            put(HIDE_SYSTEM_APPS, settings.hideSystemApps)
            put(SUPPRESS_DENY_FALLBACK_NOTICE, settings.suppressDenyFallbackNotice)
            put(SUPPRESS_PERMISSION_FLAG_NOTICE, settings.suppressPermissionFlagNotice)
            put(AUTO_APPLY_NEW_APP_TEMPLATE, settings.autoApplyNewAppTemplate)
            put(MONITOR_HEADS_UP, settings.monitorHeadsUp)
            put(SUPPRESS_BATTERY_NOTICE, settings.suppressBatteryNotice)
            put(SHOW_ALL_MONITOR_OPERATIONS, settings.showAllMonitorOperations)
            put(SUPPRESS_ALL_OPERATIONS_WARNING, settings.suppressAllOperationsWarning)
            put(SAVE_INDIVIDUAL_HISTORY, settings.saveIndividualHistory)
            settings.savedHistoryOperations?.let {
                add("$SAVED_HISTORY_OPERATIONS=${it.sorted().joinToString(",")}")
            }
        }

    fun decode(lines: List<String>): UserSettingsBackup {
        val fields = lines.keyValues()
        fun flag(key: String): Boolean? = fields[key]?.toBooleanStrictOrNull()
        return UserSettingsBackup(
            hideSystemApps = flag(HIDE_SYSTEM_APPS),
            suppressDenyFallbackNotice = flag(SUPPRESS_DENY_FALLBACK_NOTICE),
            suppressPermissionFlagNotice = flag(SUPPRESS_PERMISSION_FLAG_NOTICE),
            autoApplyNewAppTemplate = flag(AUTO_APPLY_NEW_APP_TEMPLATE),
            monitorHeadsUp = flag(MONITOR_HEADS_UP),
            suppressBatteryNotice = flag(SUPPRESS_BATTERY_NOTICE),
            showAllMonitorOperations = flag(SHOW_ALL_MONITOR_OPERATIONS),
            suppressAllOperationsWarning = flag(SUPPRESS_ALL_OPERATIONS_WARNING),
            saveIndividualHistory = flag(SAVE_INDIVIDUAL_HISTORY),
            savedHistoryOperations = fields[SAVED_HISTORY_OPERATIONS]
                ?.split(',')
                ?.map(String::trim)
                ?.filter(String::isNotEmpty)
                ?.toSet(),
        )
    }
}
