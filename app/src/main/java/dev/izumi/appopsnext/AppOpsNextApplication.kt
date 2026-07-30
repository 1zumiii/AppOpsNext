package dev.izumi.appopsnext

import android.app.Application
import dev.izumi.appopsnext.diagnostics.DiagnosticEnvironmentCollector
import dev.izumi.appopsnext.diagnostics.DiagnosticLogRepository
import dev.izumi.appopsnext.settings.UserSettingsRepository
import dev.izumi.appopsnext.shizuku.PrivilegedServiceClient
import dev.izumi.appopsnext.templates.PermissionTemplateRepository
import dev.izumi.appopsnext.history.HistoryPermissionSettingsRepository

class AppOpsNextApplication : Application() {
    val diagnosticLogRepository: DiagnosticLogRepository by lazy {
        DiagnosticLogRepository(this)
    }

    val privilegedServiceClient: PrivilegedServiceClient by lazy {
        PrivilegedServiceClient(this, diagnosticLogRepository)
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
    }
}
