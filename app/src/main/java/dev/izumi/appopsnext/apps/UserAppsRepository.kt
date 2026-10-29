package dev.izumi.appopsnext.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle
import dev.izumi.appopsnext.appops.PrivilegedAppOpsGateway
import dev.izumi.appopsnext.appops.UserUids
import dev.izumi.appopsnext.apps.model.AndroidUser
import dev.izumi.appopsnext.apps.model.InstalledApp
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * The Android users AppOps can be viewed for, and the apps installed in them.
 *
 * A null user id means the user this app runs in everywhere, so the paths that
 * only know the current user keep working unchanged.
 */
class UserAppsRepository(
    private val context: Context,
    private val gateway: PrivilegedAppOpsGateway,
) {
    val currentUserId: Int = UserUids.userIdOf(Process.myUid())

    private val mutableSelectedUserId = MutableStateFlow<Int?>(null)

    /** Null while the current user is selected. */
    val selectedUserId: StateFlow<Int?> = mutableSelectedUserId.asStateFlow()

    @Volatile
    private var appsByUser = emptyMap<Int, List<InstalledApp>>()

    fun select(userId: Int?) {
        mutableSelectedUserId.value = userId.takeIf { it != currentUserId }
    }

    /** At least the current user. Needs the privileged backend for the others. */
    suspend fun loadUsers(): List<AndroidUser> {
        val own = AndroidUser(currentUserId, "", 0)
        val result = try {
            gateway.listUsers()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return listOf(own)
        }
        if (result.timedOut || result.exitCode != 0) return listOf(own)
        return UserListParser.parse(result.stdout).ifEmpty { listOf(own) }
    }

    /** Apps of [userId], which must not be the current user. */
    suspend fun loadApps(userId: Int): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            val result = gateway.listPackagesForUser(userId)
            check(!result.timedOut && result.exitCode == 0) { "Package list failed" }
            val labels = launcherLabels(userId)
            val times = packageTimes(userId)
            val packageManager = context.packageManager
            val collator = Collator.getInstance(Locale.getDefault())
            UserPackageListParser.parse(result.stdout)
                .map { entry ->
                    InstalledApp(
                        label = labels[entry.packageName] ?: entry.packageName,
                        packageName = entry.packageName,
                        uid = entry.uid,
                        isSystemApp = isSystemInCurrentUser(packageManager, entry.packageName),
                        userId = userId,
                        firstInstallTimeMillis = times[entry.packageName]?.firstInstallTimeMillis,
                        lastUpdateTimeMillis = times[entry.packageName]?.lastUpdateTimeMillis,
                    )
                }
                .sortedWith { left, right -> collator.compare(left.label, right.label) }
                .also { apps -> appsByUser = appsByUser + (userId to apps) }
        }

    /** Empty when the dump fails: the list then shows no times and keeps name order. */
    private suspend fun packageTimes(userId: Int): Map<String, PackageTimes> =
        try {
            val result = gateway.getPackageTimes()
            if (result.timedOut || result.exitCode != 0) emptyMap()
            else PackageTimesParser.parse(result.stdout, userId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyMap()
        }

    /** Packages that share [uid], read from the last list loaded for its user. */
    fun packagesForUid(uid: Int): List<String> {
        val userId = UserUids.userIdOf(uid)
        if (userId == currentUserId) {
            return context.packageManager.getPackagesForUid(uid)?.toList().orEmpty()
        }
        return appsByUser[userId].orEmpty()
            .filter { it.uid == uid }
            .map(InstalledApp::packageName)
            .sorted()
    }

    /** Launcher icon of [packageName] in [userId], or null when it has no launcher entry. */
    fun loadIcon(packageName: String, userId: Int): Drawable? =
        runCatching {
            val launcherApps = context.getSystemService(LauncherApps::class.java)
            val handle = handleOf(launcherApps, userId) ?: return null
            launcherApps.getActivityList(packageName, handle)
                .firstOrNull()
                ?.getBadgedIcon(0)
        }.getOrNull()

    /**
     * Only profiles of the current user are visible to `LauncherApps`. Other
     * users are shown by package name.
     */
    private fun launcherLabels(userId: Int): Map<String, String> =
        runCatching {
            val launcherApps = context.getSystemService(LauncherApps::class.java)
            val handle = handleOf(launcherApps, userId) ?: return emptyMap()
            launcherApps.getActivityList(null, handle)
                .groupBy { it.applicationInfo.packageName }
                .mapValues { (_, activities) -> activities.first().label.toString() }
        }.getOrDefault(emptyMap())

    // The identifier of a UserHandle is also its hash code.
    private fun handleOf(launcherApps: LauncherApps, userId: Int): UserHandle? =
        launcherApps.profiles.firstOrNull { it.hashCode() == userId }

    private fun isSystemInCurrentUser(
        packageManager: PackageManager,
        packageName: String,
    ): Boolean =
        runCatching {
            packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(0),
            ).flags and (
                ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
                ) != 0
        }.getOrDefault(false)
}
