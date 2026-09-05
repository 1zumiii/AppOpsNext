package dev.izumi.appopsnext

import android.app.Application
import dev.izumi.appopsnext.appops.AppOpsRepository
import dev.izumi.appopsnext.diagnostics.DiagnosticEnvironmentCollector
import dev.izumi.appopsnext.diagnostics.DiagnosticLogRepository
import dev.izumi.appopsnext.settings.UserSettingsRepository
import dev.izumi.appopsnext.shizuku.PrivilegedServiceClient
import dev.izumi.appopsnext.templates.PermissionTemplateRepository
import dev.izumi.appopsnext.history.HistoryPermissionSettingsRepository
import dev.izumi.appopsnext.newapps.NewAppPolicyCoordinator
import dev.izumi.appopsnext.newapps.NewAppPolicyStateRepository
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

    val installedAppsRepository by lazy {
        dev.izumi.appopsnext.apps.InstalledAppsRepository(this)
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
    }
}
