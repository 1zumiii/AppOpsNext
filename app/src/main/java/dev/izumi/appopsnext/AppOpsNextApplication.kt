package dev.izumi.appopsnext

import android.app.Application
import dev.izumi.appopsnext.appops.AppOpsRepository
import dev.izumi.appopsnext.apps.InstalledAppsRepository
import dev.izumi.appopsnext.diagnostics.DiagnosticEnvironmentCollector
import dev.izumi.appopsnext.diagnostics.DiagnosticLogRepository
import dev.izumi.appopsnext.settings.UserSettingsRepository
import dev.izumi.appopsnext.shizuku.PrivilegedServiceClient
import dev.izumi.appopsnext.templates.PermissionTemplateRepository
import dev.izumi.appopsnext.history.HistoryPermissionSettingsRepository
import dev.izumi.appopsnext.history.AppOpsHistoryRepository
import dev.izumi.appopsnext.history.HistoryArchiveRecorder
import dev.izumi.appopsnext.history.HistoryArchiveStore
import dev.izumi.appopsnext.history.HistorySnapshotStore
import dev.izumi.appopsnext.monitor.AppOpsMonitorController
import dev.izumi.appopsnext.monitor.MonitorEventLog
import dev.izumi.appopsnext.monitor.MonitorTargetsRepository
import dev.izumi.appopsnext.newapps.NewAppPolicyCoordinator
import dev.izumi.appopsnext.newapps.NewAppPolicyStateRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppOpsNextApplication : Application() {
    private val applicationScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val diagnosticLogRepository: DiagnosticLogRepository by lazy {
        DiagnosticLogRepository(this)
    }

    val privilegedServiceClient: PrivilegedServiceClient by lazy {
        PrivilegedServiceClient(this, diagnosticLogRepository)
    }

    val historySnapshotStore: HistorySnapshotStore by lazy {
        HistorySnapshotStore(
            File(noBackupFilesDir, "history-snapshots-v1.bin"),
        )
    }
    /** Kept out of backups like the snapshots: it is a record of other apps' accesses. */
    val historyArchiveStore: HistoryArchiveStore by lazy {
        HistoryArchiveStore(File(noBackupFilesDir, "history-archive-v1.bin"))
    }

    val installedAppsRepository: InstalledAppsRepository by lazy {
        InstalledAppsRepository(this)
    }

    val appOpsRepository: AppOpsRepository by lazy {
        AppOpsRepository(
            privilegedGateway = privilegedServiceClient,
            onCancelledWrite = { write ->
                diagnosticLogRepository.warning(
                    source = "AppOpsWrite",
                    message = "Cancelled write cleanup. " +
                        "package=${write.packageName}, " +
                        "operation=${write.operation.stableName}, " +
                        "scope=${write.scope.name}, " +
                        "phase=${write.result.phase.name}, " +
                        "restoration=${write.result.restorationStatus.name}",
                )
            },
        )
    }

    val userSettingsRepository: UserSettingsRepository by lazy {
        UserSettingsRepository(this)
    }

    val permissionTemplateRepository: PermissionTemplateRepository by lazy {
        PermissionTemplateRepository(this)
    }

    val historyPermissionSettingsRepository:
        HistoryPermissionSettingsRepository by lazy {
            HistoryPermissionSettingsRepository(this)
        }

    private val newAppPolicyStateRepository by lazy {
        NewAppPolicyStateRepository(this)
    }

    val newAppPolicyCoordinator by lazy {
        NewAppPolicyCoordinator(
            context = this,
            scope = applicationScope,
            settingsRepository = userSettingsRepository,
            stateRepository = newAppPolicyStateRepository,
            templateRepository = permissionTemplateRepository,
            privilegedServiceClient = privilegedServiceClient,
            diagnosticLog = diagnosticLogRepository,
            appOpsRepository = appOpsRepository,
        )
    }

    val historyArchiveRecorder: HistoryArchiveRecorder by lazy {
        HistoryArchiveRecorder(
            scope = applicationScope,
            settingsRepository = userSettingsRepository,
            panelRepository = historyPermissionSettingsRepository,
            historyRepository = AppOpsHistoryRepository(privilegedServiceClient),
            loadInstalledApps = { installedAppsRepository.loadInstalledApps() },
            snapshotStore = historySnapshotStore,
            archiveStore = historyArchiveStore,
            privilegedState = privilegedServiceClient.state,
        )
    }

    val monitorTargetsRepository: MonitorTargetsRepository by lazy {
        MonitorTargetsRepository(this)
    }

    /** Kept out of backups like the history: it is a record of other apps' accesses. */
    val monitorEventLog: MonitorEventLog by lazy {
        MonitorEventLog(File(noBackupFilesDir, "monitor-events-v1.bin"), applicationScope)
    }

    val appOpsMonitorController: AppOpsMonitorController by lazy {
        AppOpsMonitorController(
            context = this,
            scope = applicationScope,
            installedAppsRepository = installedAppsRepository,
            gateway = privilegedServiceClient,
            targetsRepository = monitorTargetsRepository,
            settingsRepository = userSettingsRepository,
            diagnosticLog = diagnosticLogRepository,
            eventLog = monitorEventLog,
        )
    }

    override fun onCreate() {
        super.onCreate()
        val environment = DiagnosticEnvironmentCollector.collect(this)
        diagnosticLogRepository.info(
            source = "Application",
            message =
                "Process started. app=${environment.appVersionName}" +
                    "(${environment.appVersionCode}), " +
                    "device=${environment.manufacturer} " +
                    "${environment.model}, " +
                    "android=${environment.androidVersion}" +
                    "(API ${environment.apiLevel}), " +
                    "user=${environment.userHandle}, " +
                    "processUid=${environment.processUid}, " +
                    "shizukuManager=${environment.shizukuManagerVersion}",
        )
        newAppPolicyCoordinator.start()
        historyArchiveRecorder.start()
    }
}
