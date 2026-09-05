package dev.izumi.appopsnext.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dev.izumi.appopsnext.apps.model.InstalledApp
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InstalledAppsRepository(
    context: Context,
) {
    private val packageManager = context.packageManager

    private val cacheMutex = Mutex()
    private var cached: List<InstalledApp>? = null
    private var cachedLocale: Locale? = null
    private var cachedAtNanos = 0L

    suspend fun loadInstalledApps(forceRefresh: Boolean = false): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            cacheMutex.withLock {
                val locale = Locale.getDefault()
                val now = System.nanoTime()
                val previous = cached
                if (!forceRefresh && previous != null && cachedLocale == locale &&
                    now - cachedAtNanos < 60_000_000_000L) return@withLock previous
                val labelCollator = Collator.getInstance(Locale.getDefault())
                val loaded = packageManager
                    .getInstalledApplications(
                        PackageManager.ApplicationInfoFlags.of(0),
                    )
                    .map(::toInstalledApp)
                    .sortedWith { left, right ->
                        labelCollator.compare(left.label, right.label)
                    }
                cached = loaded
                cachedLocale = locale
                cachedAtNanos = System.nanoTime()
                loaded
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
