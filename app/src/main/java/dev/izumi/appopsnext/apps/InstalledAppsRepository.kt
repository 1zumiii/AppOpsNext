package dev.izumi.appopsnext.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dev.izumi.appopsnext.apps.model.InstalledApp
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InstalledAppsRepository(
    context: Context,
) {
    private val packageManager = context.packageManager

    suspend fun loadInstalledApps(): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            val labelCollator = Collator.getInstance(Locale.getDefault())
            packageManager
                .getInstalledApplications(
                    PackageManager.ApplicationInfoFlags.of(0),
                )
                .map(::toInstalledApp)
                .sortedWith { left, right ->
                    labelCollator.compare(left.label, right.label)
                }
        }

    private fun toInstalledApp(applicationInfo: ApplicationInfo): InstalledApp {
        val label = runCatching {
            applicationInfo.loadLabel(packageManager).toString()
        }.getOrDefault(applicationInfo.packageName)

        return InstalledApp(
            label = label.ifBlank { applicationInfo.packageName },
            packageName = applicationInfo.packageName,
            uid = applicationInfo.uid,
            isSystemApp = applicationInfo.flags.hasAnyFlag(
                ApplicationInfo.FLAG_SYSTEM or
                    ApplicationInfo.FLAG_UPDATED_SYSTEM_APP,
            ),
        )
    }

    private fun Int.hasAnyFlag(flags: Int): Boolean = this and flags != 0
}
