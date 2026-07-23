package dev.izumi.appops

import android.app.Application
import dev.izumi.appops.shizuku.PrivilegedServiceClient

class AppOpsApplication : Application() {
    val privilegedServiceClient: PrivilegedServiceClient by lazy {
        PrivilegedServiceClient(this)
    }
}
