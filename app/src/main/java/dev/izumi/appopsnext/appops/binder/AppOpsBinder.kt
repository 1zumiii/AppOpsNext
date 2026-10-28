package dev.izumi.appopsnext.appops.binder

import android.os.IBinder
import android.os.Parcel
import rikka.shizuku.ShizukuBinderWrapper

/**
 * The AppOps system service, reached through Shizuku's binder forwarding.
 *
 * This deliberately does not use a Shizuku UserService. The one reported
 * incompatibility in `docs/PRIVILEGED_BACKENDS.md` was a UserService bind whose
 * connection callback never arrived, on a device where Shizuku's own binder was
 * working; forwarding a transaction uses that working layer instead.
 */
internal object AppOpsBinder {
    fun service(): IBinder {
        HiddenApiAccess.ensureExempted()
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

    /** Sends one call and hands the reply, past its exception header, to [read]. */
    fun <T> call(
        service: IBinder,
        code: Int,
        write: (Parcel) -> Unit,
        read: (Parcel) -> T,
    ): T {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(AppOpsTransactions.INTERFACE)
            write(data)
            service.transact(code, data, reply, 0)
            check(reply.dataAvail() >= 4) { "Missing AppOps transaction reply" }
            reply.readException()
            return read(reply)
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private const val APP_OPS_SERVICE = "appops"
}
