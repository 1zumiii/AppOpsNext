package dev.izumi.appopsnext

import android.app.Application
import dev.izumi.appopsnext.shizuku.PrivilegedServiceClient

class AppOpsNextApplication : Application() {
    val privilegedServiceClient: PrivilegedServiceClient by lazy {
        PrivilegedServiceClient(this)
    }
}
