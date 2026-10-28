package dev.izumi.appopsnext.appops.binder

import android.app.AppOpsManager

/** One operation as the running platform defines it. */
data class DeviceOp(
    val code: Int,
    /** `opToName`, the spelling `cmd appops` and `dumpsys appops` print, such as `CAMERA`. */
    val shellName: String,
    /** `opToPublicName`, such as `android:camera`. */
    val publicName: String,
)

/** The operation table read from `AppOpsManager` on this device. */
internal object PlatformOpNames {
    /** Null when the platform refuses the reflection; callers fall back to the built-in table. */
    fun read(): List<DeviceOp>? = runCatching {
        HiddenApiAccess.ensureExempted()
        val manager = AppOpsManager::class.java
        val count = manager.getMethod("getNumOps").invoke(null) as Int
        val opToName = manager.getMethod("opToName", Int::class.java)
        val opToPublicName = manager.getMethod("opToPublicName", Int::class.java)
        (0 until count).mapNotNull { code ->
            // A retired code can throw or return a placeholder; leave it out.
            runCatching {
                DeviceOp(
                    code = code,
                    shellName = opToName.invoke(null, code) as String,
                    publicName = opToPublicName.invoke(null, code) as String,
                )
            }.getOrNull()?.takeIf { it.shellName.isNotBlank() && it.publicName.isNotBlank() }
        }
    }.getOrNull()?.takeIf { it.isNotEmpty() }
}
