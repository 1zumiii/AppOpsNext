package dev.izumi.appopsnext.guard

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import dev.izumi.appopsnext.appops.binder.AppOpsBinder
import dev.izumi.appopsnext.appops.binder.AppOpsTransactions

/**
 * `IAppOpsCallback`, called by the system when a mode of a watched operation
 * changes for any package, including the changes this app makes itself.
 */
internal class ModeChangeCallback(
    private val onChanged: (opCode: Int, uid: Int, packageName: String?) -> Unit,
) : Binder() {
    init { attachInterface(null, DESCRIPTOR) }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == INTERFACE_TRANSACTION) {
            reply?.writeString(DESCRIPTOR)
            return true
        }
        if (code != FIRST_CALL_TRANSACTION) return super.onTransact(code, data, reply, flags)
        // opChanged(int op, int uid, String packageName, String persistentDeviceId); the
        // device id was added in API 35 and is not needed, so it is not read.
        runCatching {
            data.enforceInterface(DESCRIPTOR)
            onChanged(data.readInt(), data.readInt(), data.readString())
        }
        return true
    }

    private companion object {
        const val DESCRIPTOR = "com.android.internal.app.IAppOpsCallback"
    }
}

/** Registers one [ModeChangeCallback] per operation; the platform keys watches by callback. */
internal class GuardModeWatch {
    private var service: IBinder? = null
    private val callbacks = mutableListOf<ModeChangeCallback>()

    @Synchronized
    fun watch(opCodes: Set<Int>, onChanged: (opCode: Int, uid: Int, packageName: String?) -> Unit) {
        stop()
        if (opCodes.isEmpty()) return
        val appOps = AppOpsBinder.service()
        service = appOps
        val start = AppOpsTransactions.startWatchingMode().value
        for (code in opCodes) {
            val callback = ModeChangeCallback(onChanged)
            AppOpsBinder.call(
                appOps,
                start,
                write = { data ->
                    data.writeInt(code)
                    data.writeString(null)
                    data.writeStrongBinder(callback)
                },
                read = {},
            )
            callbacks += callback
        }
    }

    @Synchronized
    fun stop() {
        val appOps = service
        if (appOps != null) {
            val stop = AppOpsTransactions.stopWatchingMode().value
            callbacks.forEach { callback ->
                runCatching {
                    AppOpsBinder.call(appOps, stop, write = { it.writeStrongBinder(callback) }, read = {})
                }
            }
        }
        callbacks.clear()
        service = null
    }

    val isAlive: Boolean
        @Synchronized get() = runCatching { service?.pingBinder() == true }.getOrDefault(false)
}
