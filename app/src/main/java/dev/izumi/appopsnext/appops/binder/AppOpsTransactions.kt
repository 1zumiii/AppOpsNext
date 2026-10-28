package dev.izumi.appopsnext.appops.binder

import android.os.Build

/**
 * Resolves binder transaction codes for `IAppOpsService` methods.
 *
 * Two routes, because either one alone can be wrong on a given image:
 *
 * 1. Read `IAppOpsService$Stub.TRANSACTION_<method>` from the running platform.
 *    This is always correct when it works, but the class is internal and the
 *    read can be refused by the hidden-API policy.
 * 2. Fall back to the position the method holds in the platform sources for
 *    this API level. Android 17 moved `checkOperationForDevice` from the end of
 *    the interface to position 12, which shifted positions 12 to 54 by one.
 *    A vendor image that reorders the interface still defeats this route.
 *
 * Which route answered is recorded so a failure can be attributed instead of
 * guessed at.
 */
internal object AppOpsTransactions {
    data class Code(val value: Int, val fromPlatform: Boolean)

    fun getPackagesForOps(): Code = resolve("getPackagesForOps")

    fun getOpsForPackage(): Code = resolve("getOpsForPackage")

    fun getUidOps(): Code = resolve("getUidOps")

    fun startWatchingActive(): Code = resolve("startWatchingActive")

    fun stopWatchingActive(): Code = resolve("stopWatchingActive")

    fun startWatchingStarted(): Code = resolve("startWatchingStarted")

    fun stopWatchingStarted(): Code = resolve("stopWatchingStarted")

    fun startWatchingNoted(): Code = resolve("startWatchingNoted")

    fun stopWatchingNoted(): Code = resolve("stopWatchingNoted")

    /** The source position of [methodName] on [sdkInt], counted from 1 like transaction codes. */
    fun fallbackCode(methodName: String, sdkInt: Int): Int {
        val position = checkNotNull(API_36_POSITIONS[methodName]) { "Unknown method $methodName" }
        return if (sdkInt >= API_37 && position in SHIFTED_IN_API_37) position + 1 else position
    }

    private fun resolve(methodName: String): Code {
        HiddenApiAccess.ensureExempted()
        val fromPlatform = runCatching {
            val stub = Class.forName("$INTERFACE\$Stub")
            val field = stub.getDeclaredField("TRANSACTION_$methodName")
            field.isAccessible = true
            field.getInt(null)
        }.getOrNull()
        return if (fromPlatform != null && fromPlatform > 0) {
            Code(fromPlatform, fromPlatform = true)
        } else {
            Code(fallbackCode(methodName, Build.VERSION.SDK_INT), fromPlatform = false)
        }
    }

    /** Identical in API 35 and 36, including 36 QPR2. */
    private val API_36_POSITIONS = mapOf(
        "getPackagesForOps" to 18,
        "getOpsForPackage" to 19,
        "getUidOps" to 29,
        "startWatchingActive" to 37,
        "stopWatchingActive" to 38,
        "startWatchingStarted" to 41,
        "stopWatchingStarted" to 42,
        "startWatchingNoted" to 43,
        "stopWatchingNoted" to 44,
    )
    private val SHIFTED_IN_API_37 = 12..54
    private const val API_37 = 37

    const val INTERFACE = "com.android.internal.app.IAppOpsService"
}
