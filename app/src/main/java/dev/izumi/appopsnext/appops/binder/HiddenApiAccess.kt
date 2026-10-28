package dev.izumi.appopsnext.appops.binder

import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Lifts the hidden-API policy for the few platform classes the binder reads
 * need. When the library cannot do it on this runtime, plain reflection is
 * still tried, and a refusal there sends the caller to the text path.
 */
internal object HiddenApiAccess {
    @Volatile
    var exempted: Boolean? = null
        private set

    @Synchronized
    fun ensureExempted(): Boolean {
        exempted?.let { return it }
        val result = runCatching {
            HiddenApiBypass.addHiddenApiExemptions(*PREFIXES)
        }.getOrDefault(false)
        exempted = result
        return result
    }

    private val PREFIXES = arrayOf(
        "Landroid/app/AppOpsManager",
        "Landroid/os/ServiceManager",
        "Lcom/android/internal/app/IAppOpsService",
    )
}
