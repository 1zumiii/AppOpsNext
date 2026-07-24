package dev.izumi.appopsnext

import android.app.Application
import dev.izumi.appopsnext.settings.UserSettingsRepository
import dev.izumi.appopsnext.shizuku.PrivilegedServiceClient
import dev.izumi.appopsnext.templates.PermissionTemplateRepository
import dev.izumi.appopsnext.history.HistoryPermissionSettingsRepository

class AppOpsNextApplication : Application() {
    val privilegedServiceClient: PrivilegedServiceClient by lazy {
        PrivilegedServiceClient(this)
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
}
