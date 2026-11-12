package dev.izumi.appopsnext

import android.app.Application
import android.os.Process
import android.content.pm.PackageManager
import dev.izumi.appopsnext.appops.AppOpsReadCapabilities
import dev.izumi.appopsnext.appops.AppOpsReadProbe
import dev.izumi.appopsnext.appops.AppOpsRepository
import dev.izumi.appopsnext.appops.UserUids
import dev.izumi.appopsnext.appops.changes.AppOpChangeDriftMonitor
import dev.izumi.appopsnext.appops.changes.AppOpChangeLog
import dev.izumi.appopsnext.appops.StructuredAppOpsReads
import dev.izumi.appopsnext.appops.model.PackageOpsLoadResult
import dev.izumi.appopsnext.appops.RevokedCompatWriteMemory
import dev.izumi.appopsnext.apps.InstalledAppsRepository
import dev.izumi.appopsnext.apps.UserAppsRepository
import dev.izumi.appopsnext.backup.AppBackupService
import dev.izumi.appopsnext.diagnostics.DiagnosticEnvironmentCollector
import dev.izumi.appopsnext.diagnostics.DiagnosticLogRepository
import dev.izumi.appopsnext.guard.GuardController
import dev.izumi.appopsnext.guard.GuardNotifier
import dev.izumi.appopsnext.guard.GuardRepository
import dev.izumi.appopsnext.settings.UserSettingsRepository
import dev.izumi.appopsnext.shizuku.PrivilegedServiceClient
import dev.izumi.appopsnext.shizuku.model.PrivilegedServiceState
import dev.izumi.appopsnext.templates.PermissionTemplateRepository
import dev.izumi.appopsnext.history.HistoryPermissionSettingsRepository
import dev.izumi.appopsnext.history.AppOpsHistoryRepository
import dev.izumi.appopsnext.history.HistoryArchiveRecorder
import dev.izumi.appopsnext.history.HistoryArchiveStore
import dev.izumi.appopsnext.history.HistorySnapshotStore
import dev.izumi.appopsnext.monitor.AppOpsMonitorController
import dev.izumi.appopsnext.monitor.MonitorEventLog
import dev.izumi.appopsnext.monitor.MonitorTargetsRepository
import dev.izumi.appopsnext.monitor.VendorMonitorOps
import dev.izumi.appopsnext.newapps.NewAppPolicyCoordinator
import dev.izumi.appopsnext.newapps.NewAppPolicyStateRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AppOpsNextApplication : Application() {
    val revokedCompatWriteMemory = RevokedCompatWriteMemory()
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

    val userAppsRepository: UserAppsRepository by lazy {
        UserAppsRepository(this, privilegedServiceClient)
    }

    val structuredAppOpsReads: StructuredAppOpsReads by lazy {
        StructuredAppOpsReads(
            packageManager = packageManager,
            onFailure = { capability, error ->
                applicationScope.launch {
                    appOpsReadCapabilities.invalidate(capability, error)
                }
            },
        )
    }

    val appOpsReadCapabilities: AppOpsReadCapabilities by lazy {
        AppOpsReadCapabilities(
            context = this,
            structured = structuredAppOpsReads,
            probe = AppOpsReadProbe(
                structured = structuredAppOpsReads,
                textPackageOps = { packageName, uid ->
                    (appOpsRepository.loadPackageOpsFromText(packageName, uid)
                        as? PackageOpsLoadResult.Success)?.snapshot
                },
                textOpStates = appOpsRepository::loadOpStatesFromText,
            ),
            installedApps = installedAppsRepository,
            log = diagnosticLogRepository,
        )
    }

    val appOpsRepository: AppOpsRepository by lazy {
        AppOpsRepository(
            privilegedGateway = privilegedServiceClient,
            structuredReads = structuredAppOpsReads,
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
            onModeChanged = appOpChangeLog::record,
            onWriteFailed = { write ->
                val result = write.result
                diagnosticLogRepository.warning(
                    source = "AppOpsWrite",
                    message = "Write failed. " +
                        "package=${write.packageName}, " +
                        "user=${write.userId ?: "current"}, " +
                        "operation=${write.operation.stableName}, " +
                        "scope=${write.scope.name}, " +
                        "requested=${write.requestedMode.name}, " +
                        "phase=${result.phase.name}, " +
                        "original=${result.originalMode?.name}, " +
                        "observed=${result.observedMode?.name}, " +
                        "restoration=${result.restorationStatus.name}",
                )
            },
        )
    }

    val appOpChangeLog: AppOpChangeLog by lazy {
        AppOpChangeLog(this, currentUserId = UserUids.userIdOf(Process.myUid()))
    }

    val appOpChangeDriftMonitor: AppOpChangeDriftMonitor by lazy {
        AppOpChangeDriftMonitor(
            records = appOpChangeLog.records,
            connected = privilegedServiceClient.state
                .map { it is PrivilegedServiceState.Connected }
                .distinctUntilChanged(),
            loadApps = { userId ->
                if (userId == userAppsRepository.currentUserId) {
                    installedAppsRepository.loadInstalledApps()
                } else {
                    userAppsRepository.loadApps(userId)
                }
            },
            loadOpStates = appOpsRepository::loadOpStates,
            scope = applicationScope,
        )
    }

    val guardRepository: GuardRepository by lazy { GuardRepository(this) }

    val guardNotifier: GuardNotifier by lazy { GuardNotifier(this) }

    val guardController: GuardController by lazy {
        GuardController(
            context = this,
            repository = guardRepository,
            appOpsRepository = appOpsRepository,
            privilegedServiceClient = privilegedServiceClient,
            diagnosticLog = diagnosticLogRepository,
            scope = applicationScope,
            writeMemory = revokedCompatWriteMemory,
            notifier = guardNotifier,
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
            restorableChanges = appOpChangeLog::restorable,
            writeMemory = revokedCompatWriteMemory,
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

    val appBackupService: AppBackupService by lazy {
        AppBackupService(
            templateRepository = permissionTemplateRepository,
            settingsRepository = userSettingsRepository,
            monitorTargetsRepository = monitorTargetsRepository,
            guardRepository = guardRepository,
            appVersion = BuildConfig.VERSION_NAME,
            packageUid = { packageName ->
                runCatching {
                    packageManager.getPackageUid(packageName, PackageManager.PackageInfoFlags.of(0))
                }.getOrNull()
            },
        )
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
            probeVendorOps = ::probeVendorOps,
        )
    }

    /**
     * Whether this device has MIUI private operations the monitor can offer,
     * see [VendorMonitorOps]. [packages] are read first, then this app and a
     * few user apps, which the security center is most likely to have set.
     */
    suspend fun probeVendorOps(packages: Collection<String> = emptyList()): Set<Int> {
        VendorMonitorOps.codes.value?.takeIf { it.isNotEmpty() }?.let { return it }
        val userApps = installedAppsRepository.loadInstalledApps()
            .filterNot { it.isSystemApp }
            .map { it.packageName }
        return VendorMonitorOps.discovery.probe(packages.toList() + packageName + userApps) { target ->
            (appOpsRepository.loadPackageOps(target) as? PackageOpsLoadResult.Success)
                ?.snapshot?.entries?.map { it.name }
        }
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
        guardController.start()
        applicationScope.launch {
            privilegedServiceClient.state
                .filterIsInstance<PrivilegedServiceState.Connected>()
                .collect { appOpsReadCapabilities.resolve() }
        }
    }
}
