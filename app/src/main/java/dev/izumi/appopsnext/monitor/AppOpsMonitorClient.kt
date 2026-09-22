package dev.izumi.appopsnext.monitor

import android.os.IBinder
import android.os.Parcel
import rikka.shizuku.ShizukuBinderWrapper

/**
 * Registers AppOps watch callbacks through Shizuku's remote binder call.
 *
 * This deliberately does not use a Shizuku UserService. The one reported
 * incompatibility in `docs/PRIVILEGED_BACKENDS.md` was a UserService bind whose
 * connection callback never arrived, on a device where Shizuku's own binder was
 * working; forwarding a transaction uses that working layer instead.
 */
internal class AppOpsMonitorClient {
    /** Which of the three watches are live, so a partial result stays visible. */
    data class Registration(
        val active: Boolean,
        val noted: Boolean,
        val started: Boolean,
        val transactionCodesFromPlatform: Boolean,
        val failures: List<String>,
    ) {
        val any: Boolean get() = active || noted || started
    }

    private var activeCallback: ActiveOpCallback? = null
    private var notedCallback: NotedOpCallback? = null
    private var startedCallback: StartedOpCallback? = null
    private var registeredService: IBinder? = null

    /**
     * Each watch is registered on its own. A refused `noted` watch still leaves
     * the long-running sensor accesses reported, which is more useful than
     * abandoning the whole feature because one of three calls failed.
     *
     * @throws IllegalStateException when the AppOps service itself is unreachable.
     */
    @Synchronized
    fun register(
        opCodes: IntArray,
        onAccess: (AppOpAccessEvent) -> Unit,
        onMalformed: (String) -> Unit,
    ): Registration {
        unregister()
        check(registeredService == null) { "Previous AppOps watches could not be removed; retry later" }
        val service = appOpsService()
        registeredService = service
        val failures = mutableListOf<String>()
        var fromPlatform = true

        fun <T : IBinder> watch(
            code: AppOpsTransactions.Code,
            label: String,
            callback: T,
        ): T? {
            if (!code.fromPlatform) fromPlatform = false
            return runCatching {
                transactWatch(service, code.value, opCodes, callback)
                callback
            }.getOrElse { error ->
                failures += "$label: ${error.message ?: error::class.java.simpleName}"
                null
            }
        }

        activeCallback = watch(
            AppOpsTransactions.startWatchingActive(),
            "active",
            ActiveOpCallback(onAccess, onMalformed),
        )
        notedCallback = watch(
            AppOpsTransactions.startWatchingNoted(),
            "noted",
            NotedOpCallback(onAccess, onMalformed),
        )
        startedCallback = watch(
            AppOpsTransactions.startWatchingStarted(),
            "started",
            StartedOpCallback(onAccess, onMalformed),
        )

        return Registration(
            active = activeCallback != null,
            noted = notedCallback != null,
            started = startedCallback != null,
            transactionCodesFromPlatform = fromPlatform,
            failures = failures,
        )
    }

    @Synchronized
    fun unregister() {
        // Unregister from the exact binder used for registration, never a replacement service.
        val service = registeredService
        if (service == null || !service.isBinderAlive) {
            activeCallback = null
            notedCallback = null
            startedCallback = null
            registeredService = null
            return
        }
        // Keep failed handles for the next cleanup attempt instead of leaking a live watch.
        fun <T : IBinder> stop(callback: T?, code: () -> AppOpsTransactions.Code): T? {
            if (callback == null) return null
            return try {
                transactStop(service, code().value, callback)
                null
            } catch (_: Exception) {
                callback
            }
        }
        activeCallback = stop(activeCallback, AppOpsTransactions::stopWatchingActive)
        notedCallback = stop(notedCallback, AppOpsTransactions::stopWatchingNoted)
        startedCallback = stop(startedCallback, AppOpsTransactions::stopWatchingStarted)
        if (activeCallback == null && notedCallback == null && startedCallback == null) {
            registeredService = null
        }
    }

    /** True while the registered callbacks still have a live service behind them. */
    @Synchronized
    fun isServiceAlive(): Boolean = runCatching {
        registeredService?.pingBinder() == true
    }.getOrDefault(false)

    private fun appOpsService(): IBinder {
        // Do not use Shizuku's permanent service cache: a dead proxy must be replaced.
        val raw = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, APP_OPS_SERVICE) as? IBinder
            ?: error("The AppOps system service is unavailable")
        // A descriptor that is not the one the transaction codes belong to means
        // a call would land on an unrelated method, so refuse rather than guess.
        check(raw.pingBinder()) { "The AppOps system service is dead" }
        val descriptor = raw.interfaceDescriptor
        check(descriptor == AppOpsTransactions.INTERFACE) {
            "Unexpected AppOps interface descriptor: $descriptor"
        }
        return ShizukuBinderWrapper(raw)
    }

    private fun transactWatch(
        service: IBinder,
        transaction: Int,
        opCodes: IntArray,
        callback: IBinder,
    ) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(AppOpsTransactions.INTERFACE)
            data.writeIntArray(opCodes)
            data.writeStrongBinder(callback)
            service.transact(transaction, data, reply, 0)
            check(reply.dataAvail() >= 4) { "Missing AppOps transaction reply" }
            reply.readException()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun transactStop(
        service: IBinder,
        transaction: Int,
        callback: IBinder,
    ) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(AppOpsTransactions.INTERFACE)
            data.writeStrongBinder(callback)
            service.transact(transaction, data, reply, 0)
            check(reply.dataAvail() >= 4) { "Missing AppOps transaction reply" }
            reply.readException()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private companion object {
        const val APP_OPS_SERVICE = "appops"
    }
}
