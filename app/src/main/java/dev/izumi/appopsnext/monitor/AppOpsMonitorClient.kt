package dev.izumi.appopsnext.monitor

import android.os.IBinder
import android.os.Parcel
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

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

    val isRegistered: Boolean
        get() = activeCallback != null || notedCallback != null || startedCallback != null

    /**
     * Each watch is registered on its own. A refused `noted` watch still leaves
     * the long-running sensor accesses reported, which is more useful than
     * abandoning the whole feature because one of three calls failed.
     *
     * @throws IllegalStateException when the AppOps service itself is unreachable.
     */
    fun register(
        opCodes: IntArray,
        onAccess: (AppOpAccessEvent) -> Unit,
    ): Registration {
        unregister()
        val service = appOpsService()
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
            ActiveOpCallback(onAccess),
        )
        notedCallback = watch(
            AppOpsTransactions.startWatchingNoted(),
            "noted",
            NotedOpCallback(onAccess),
        )
        startedCallback = watch(
            AppOpsTransactions.startWatchingStarted(),
            "started",
            StartedOpCallback(onAccess),
        )

        return Registration(
            active = activeCallback != null,
            noted = notedCallback != null,
            started = startedCallback != null,
            transactionCodesFromPlatform = fromPlatform,
            failures = failures,
        )
    }

    fun unregister() {
        val service = runCatching { appOpsService() }.getOrNull()
        if (service != null) {
            activeCallback?.let {
                runCatching {
                    transactStop(service, AppOpsTransactions.stopWatchingActive().value, it)
                }
            }
            notedCallback?.let {
                runCatching {
                    transactStop(service, AppOpsTransactions.stopWatchingNoted().value, it)
                }
            }
            startedCallback?.let {
                runCatching {
                    transactStop(service, AppOpsTransactions.stopWatchingStarted().value, it)
                }
            }
        }
        activeCallback = null
        notedCallback = null
        startedCallback = null
    }

    /** True while the registered callbacks still have a live service behind them. */
    fun isServiceAlive(): Boolean = runCatching {
        SystemServiceHelper.getSystemService(APP_OPS_SERVICE)?.pingBinder() == true
    }.getOrDefault(false)

    private fun appOpsService(): IBinder {
        val raw = SystemServiceHelper.getSystemService(APP_OPS_SERVICE)
            ?: error("The AppOps system service is unavailable")
        // A descriptor that is not the one the transaction codes belong to means
        // a call would land on an unrelated method, so refuse rather than guess.
        val descriptor = runCatching { raw.interfaceDescriptor }.getOrNull()
        check(descriptor == null || descriptor == AppOpsTransactions.INTERFACE) {
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
