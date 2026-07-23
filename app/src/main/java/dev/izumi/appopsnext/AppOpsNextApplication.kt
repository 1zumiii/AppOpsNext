package dev.izumi.appopsnext

import android.app.Application
import dev.izumi.appopsnext.settings.UserSettingsRepository
import dev.izumi.appopsnext.shizuku.PrivilegedServiceClient
import dev.izumi.appopsnext.templates.PermissionTemplateRepository

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
}
