package dev.izumi.appopsnext.monitor

import android.os.IBinder
import dev.izumi.appopsnext.appops.binder.AppOpsBinder
import dev.izumi.appopsnext.appops.binder.AppOpsTransactions
import dev.izumi.appopsnext.appops.parser.AccessWatchKind

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
        /** Every call made for private operations, in the order it was made. */
        val vendor: List<VendorWatchOutcome> = emptyList(),
        /** `noted [10008, 10021]: <reason>` for each private batch that was split after a refusal. */
        val vendorBatchFailures: List<String> = emptyList(),
    ) {
        val any: Boolean get() = active || noted || started
    }

    private var activeCallback: ActiveOpCallback? = null
    private var notedCallback: NotedOpCallback? = null
    private var startedCallback: StartedOpCallback? = null
    /**
     * Private operations are watched by callbacks of their own, so a refusal
     * never touches the standard watches above. A refused call's callback is
     * kept too until it has been stopped, in case a vendor build registered
     * part of the array before refusing it.
     */
    private val vendorCallbacks = mutableListOf<VendorCallback>()
    private var registeredService: IBinder? = null

    private class VendorCallback(val callback: IBinder, val stop: () -> AppOpsTransactions.Code)

    /**
     * Each watch is registered on its own. A refused `noted` watch still leaves
     * the long-running sensor accesses reported, which is more useful than
     * abandoning the whole feature because one of three calls failed.
     *
     * [vendorOpCodes] are registered after the standard watches and apart from
     * them, see [VendorWatches.registerIsolated]. With no [opCodes] the standard
     * watches are not registered at all.
     *
     * @throws IllegalStateException when the AppOps service itself is unreachable.
     */
    @Synchronized
    fun register(
        opCodes: IntArray,
        onAccess: (AppOpAccessEvent) -> Unit,
        onMalformed: (String) -> Unit,
        vendorOpCodes: IntArray = IntArray(0),
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

        if (opCodes.isNotEmpty()) {
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
        }

        val vendor = mutableListOf<VendorWatchOutcome>()
        val batchFailures = mutableListOf<String>()
        fun watchVendor(
            kind: AccessWatchKind,
            start: AppOpsTransactions.Code,
            stop: () -> AppOpsTransactions.Code,
            callback: () -> IBinder,
        ) {
            if (!start.fromPlatform) fromPlatform = false
            val result = VendorWatches.registerIsolated(vendorOpCodes.toList()) { codes ->
                val instance = callback()
                try {
                    transactWatch(service, start.value, codes, instance)
                } catch (error: Exception) {
                    stopRefused(service, instance, stop)
                    throw error
                }
                vendorCallbacks += VendorCallback(instance, stop)
                instance
            }
            result.batchFailure?.let { batchFailures += "${kind.name.lowercase()} ${vendorOpCodes.toList()}: $it" }
            result.attempts.forEach { vendor += VendorWatchOutcome(kind, it.codes, it.failure) }
        }

        if (vendorOpCodes.isNotEmpty()) {
            watchVendor(
                AccessWatchKind.ACTIVE,
                AppOpsTransactions.startWatchingActive(),
                AppOpsTransactions::stopWatchingActive,
            ) { ActiveOpCallback(onAccess, onMalformed) }
            watchVendor(
                AccessWatchKind.NOTED,
                AppOpsTransactions.startWatchingNoted(),
                AppOpsTransactions::stopWatchingNoted,
            ) { NotedOpCallback(onAccess, onMalformed) }
            watchVendor(
                AccessWatchKind.STARTED,
                AppOpsTransactions.startWatchingStarted(),
                AppOpsTransactions::stopWatchingStarted,
            ) { StartedOpCallback(onAccess, onMalformed) }
        }

        return Registration(
            active = activeCallback != null,
            noted = notedCallback != null,
            started = startedCallback != null,
            transactionCodesFromPlatform = fromPlatform,
            failures = failures,
            vendor = vendor,
            vendorBatchFailures = batchFailures,
        )
    }

    /**
     * Stopping a callback the service never registered does nothing on the
     * stock service. A stop that fails leaves the callback for [unregister].
     */
    private fun stopRefused(service: IBinder, callback: IBinder, stop: () -> AppOpsTransactions.Code) {
        try {
            transactStop(service, stop().value, callback)
        } catch (_: Exception) {
            vendorCallbacks += VendorCallback(callback, stop)
        }
    }

    @Synchronized
    fun unregister() {
        // Unregister from the exact binder used for registration, never a replacement service.
        val service = registeredService
        if (service == null || !service.isBinderAlive) {
            activeCallback = null
            notedCallback = null
            startedCallback = null
            vendorCallbacks.clear()
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
        vendorCallbacks.retainAll { stop(it.callback, it.stop) != null }
        if (activeCallback == null && notedCallback == null && startedCallback == null &&
            vendorCallbacks.isEmpty()
        ) {
            registeredService = null
        }
    }

    /** True while the registered callbacks still have a live service behind them. */
    @Synchronized
    fun isServiceAlive(): Boolean = runCatching {
        registeredService?.pingBinder() == true
    }.getOrDefault(false)

    private fun appOpsService(): IBinder = AppOpsBinder.service()

    private fun transactWatch(
        service: IBinder,
        transaction: Int,
        opCodes: IntArray,
        callback: IBinder,
    ) = AppOpsBinder.call(
        service,
        transaction,
        write = { data ->
            data.writeIntArray(opCodes)
            data.writeStrongBinder(callback)
        },
        read = {},
    )

    private fun transactStop(
        service: IBinder,
        transaction: Int,
        callback: IBinder,
    ) = AppOpsBinder.call(
        service,
        transaction,
        write = { data -> data.writeStrongBinder(callback) },
        read = {},
    )
}
