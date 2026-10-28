package dev.izumi.appopsnext.appops.binder

import android.os.IBinder
import android.os.Parcel
import android.os.Parcelable

/**
 * Calls the read methods of `IAppOpsService` and unparcels the reply with the
 * platform's own `AppOpsManager.PackageOps` creator, so the wire format is
 * always the one the running system wrote.
 *
 * Every failure is thrown; [StructuredAppOpsReads] decides what a failure
 * means and falls back to the text commands.
 */
internal class BinderAppOpsReader(
    private val serviceProvider: () -> IBinder = AppOpsBinder::service,
) {
    private val platform by lazy { PlatformPackageOps() }

    /** One service lookup for a batch of calls, such as a UID per installed app. */
    fun <T> session(block: Session.() -> T): T = Session(serviceProvider()).block()

    inner class Session(private val service: IBinder) {
        /** `null` ops asks for every operation, as `cmd appops get` does. */
        fun opsForPackage(uid: Int, packageName: String, ops: IntArray? = null): List<RawPackageOps> =
            call(AppOpsTransactions.getOpsForPackage()) { data ->
                data.writeInt(uid)
                data.writeString(packageName)
                data.writeIntArray(ops)
            }

        fun uidOps(uid: Int, ops: IntArray? = null): List<RawPackageOps> =
            call(AppOpsTransactions.getUidOps()) { data ->
                data.writeInt(uid)
                data.writeIntArray(ops)
            }

        fun packagesForOps(ops: IntArray): List<RawPackageOps> =
            call(AppOpsTransactions.getPackagesForOps()) { data ->
                data.writeIntArray(ops)
            }

        private fun call(
            code: AppOpsTransactions.Code,
            write: (Parcel) -> Unit,
        ): List<RawPackageOps> =
            AppOpsBinder.call(service, code.value, write) { reply ->
                reply.createTypedArrayList(platform.creator)
                    .orEmpty()
                    .map(platform::toRaw)
            }
    }

    private class PlatformPackageOps {
        private val packageOps = Class.forName("android.app.AppOpsManager\$PackageOps")
        private val opEntry = Class.forName("android.app.AppOpsManager\$OpEntry")

        @Suppress("UNCHECKED_CAST")
        val creator = packageOps.getField("CREATOR").get(null) as Parcelable.Creator<Parcelable>
        private val getPackageName = packageOps.getMethod("getPackageName")
        private val getUid = packageOps.getMethod("getUid")
        private val getOps = packageOps.getMethod("getOps")
        private val getOp = opEntry.getMethod("getOp")
        private val getMode = opEntry.getMethod("getMode")
        private val getLastAccessTime = opEntry.getMethod("getLastAccessTime", Int::class.java)
        private val getLastRejectTime = opEntry.getMethod("getLastRejectTime", Int::class.java)
        private val getLastDuration = opEntry.getMethod("getLastDuration", Int::class.java)

        fun toRaw(value: Parcelable): RawPackageOps =
            RawPackageOps(
                packageName = getPackageName.invoke(value) as String?,
                uid = getUid.invoke(value) as Int,
                ops = (getOps.invoke(value) as List<*>).map { entry ->
                    RawOpEntry(
                        code = getOp.invoke(entry) as Int,
                        mode = getMode.invoke(entry) as Int,
                        lastAccessTime = timeOf(getLastAccessTime, entry),
                        lastRejectTime = timeOf(getLastRejectTime, entry),
                        lastDuration = timeOf(getLastDuration, entry),
                    )
                },
            )

        /** Usage times only decorate a row, so one the platform refuses is just absent. */
        private fun timeOf(getter: java.lang.reflect.Method, entry: Any?): Long =
            runCatching { getter.invoke(entry, OP_FLAGS_ALL) as Long }.getOrDefault(-1L)
    }

    private companion object {
        /** `AppOpsManager.OP_FLAGS_ALL`, the flags `cmd appops get` reports with. */
        const val OP_FLAGS_ALL = 0x1F
    }
}
