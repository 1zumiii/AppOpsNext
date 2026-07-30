package dev.izumi.appopsnext.diagnostics

import android.content.Context
import android.os.Build
import android.os.Process
import android.os.UserHandle
import dev.izumi.appopsnext.BuildConfig
import java.util.Locale

data class DiagnosticEnvironment(
    val appVersionName: String,
    val appVersionCode: Int,
    val buildType: String,
    val targetSdk: Int,
    val compileSdk: Int,
    val manufacturer: String,
    val model: String,
    val device: String,
    val androidVersion: String,
    val apiLevel: Int,
    val securityPatch: String,
    val buildId: String,
    val userHandle: String,
    val processUid: Int,
    val locale: String,
    val supportedAbis: String,
    val shizukuApiVersion: String,
    val shizukuManagerVersion: String,
)

object DiagnosticEnvironmentCollector {
    fun collect(context: Context): DiagnosticEnvironment =
        DiagnosticEnvironment(
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE,
            buildType = BuildConfig.BUILD_TYPE,
            targetSdk = context.applicationInfo.targetSdkVersion,
            compileSdk = context.applicationInfo.compileSdkVersion,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            device = Build.DEVICE,
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            buildId = Build.ID,
            userHandle =
                UserHandle.getUserHandleForUid(Process.myUid()).toString(),
            processUid = Process.myUid(),
            locale = Locale.getDefault().toLanguageTag(),
            supportedAbis = Build.SUPPORTED_ABIS.joinToString(),
            shizukuApiVersion = BuildConfig.SHIZUKU_API_VERSION,
            shizukuManagerVersion = readShizukuManagerVersion(context),
        )

    @Suppress("DEPRECATION")
    private fun readShizukuManagerVersion(context: Context): String =
        runCatching {
            val packageInfo = context.packageManager.getPackageInfo(
                SHIZUKU_PACKAGE_NAME,
                0,
            )
            "${packageInfo.versionName.orEmpty()} (${packageInfo.longVersionCode})"
        }.getOrDefault("not installed or unavailable")

    private const val SHIZUKU_PACKAGE_NAME =
        "moe.shizuku.privileged.api"
}
