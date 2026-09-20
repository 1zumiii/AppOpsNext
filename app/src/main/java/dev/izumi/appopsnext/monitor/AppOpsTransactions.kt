package dev.izumi.appopsnext.monitor

/**
 * Resolves the binder transaction codes for the AppOps watch methods.
 *
 * Two routes, because either one alone can be wrong on a given image:
 *
 * 1. Read `IAppOpsService$Stub.TRANSACTION_<method>` from the running platform.
 *    This is always correct when it works, but the class is internal and the
 *    read can be refused by the hidden-API policy.
 * 2. Fall back to the position the method holds in the platform sources, which
 *    was identical in API 35 and API 36. This works when reflection is refused
 *    but goes wrong if a vendor image reorders the interface.
 *
 * Which route answered is recorded so a failure can be attributed instead of
 * guessed at.
 */
internal object AppOpsTransactions {
    data class Code(val value: Int, val fromPlatform: Boolean)

    fun startWatchingActive(): Code = resolve("startWatchingActive", 37)

    fun stopWatchingActive(): Code = resolve("stopWatchingActive", 38)

    fun startWatchingStarted(): Code = resolve("startWatchingStarted", 41)

    fun stopWatchingStarted(): Code = resolve("stopWatchingStarted", 42)

    fun startWatchingNoted(): Code = resolve("startWatchingNoted", 43)

    fun stopWatchingNoted(): Code = resolve("stopWatchingNoted", 44)

    private fun resolve(methodName: String, fallback: Int): Code {
        val fromPlatform = runCatching {
            val stub = Class.forName("$INTERFACE\$Stub")
            val field = stub.getDeclaredField("TRANSACTION_$methodName")
            field.isAccessible = true
            field.getInt(null)
        }.getOrNull()
        return if (fromPlatform != null && fromPlatform > 0) {
            Code(fromPlatform, fromPlatform = true)
        } else {
            Code(fallback, fromPlatform = false)
        }
    }

    const val INTERFACE = "com.android.internal.app.IAppOpsService"
}
