package dev.izumi.appopsnext.newapps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dev.izumi.appopsnext.newapps.model.InstalledPackageFingerprint
import dev.izumi.appopsnext.newapps.model.InstalledPackageRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InstalledPackageScanner(
    context: Context,
) {
    private val packageManager = context.packageManager
    private val ownPackageName = context.packageName

    suspend fun scanUserApps(): List<InstalledPackageRecord> =
        withContext(Dispatchers.IO) {
            packageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(0),
            ).mapNotNull { packageInfo ->
                val applicationInfo = packageInfo.applicationInfo
                    ?: return@mapNotNull null
                if (
                    packageInfo.packageName == ownPackageName ||
                    applicationInfo.isSystemApplication()
                ) {
                    return@mapNotNull null
                }
                val label = runCatching {
                    applicationInfo.loadLabel(packageManager).toString()
                }.getOrDefault(packageInfo.packageName)
                InstalledPackageRecord(
                    fingerprint = InstalledPackageFingerprint(
                        packageName = packageInfo.packageName,
                        firstInstallTimeMillis = packageInfo.firstInstallTime,
                    ),
                    label = label.ifBlank { packageInfo.packageName },
                    uid = applicationInfo.uid,
                )
            }
        }

    private fun ApplicationInfo.isSystemApplication(): Boolean =
        flags and (
            ApplicationInfo.FLAG_SYSTEM or
                ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
            ) != 0
}
