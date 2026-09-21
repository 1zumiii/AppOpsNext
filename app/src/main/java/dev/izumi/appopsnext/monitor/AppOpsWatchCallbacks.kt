package dev.izumi.appopsnext.monitor

import android.os.Binder
import android.os.Parcel
import java.util.concurrent.atomic.AtomicBoolean

/** The common prefix is followed by a fixed-size tail, with or without virtualDeviceId. */
internal sealed class AppOpsWatchCallback(
    private val descriptor: String,
    private val kind: AppOpAccessKind,
    private val onAccess: (AppOpAccessEvent) -> Unit,
    private val onMalformed: (String) -> Unit,
) : Binder() {
    private val reportedMalformed = AtomicBoolean(false)

    init { attachInterface(null, descriptor) }

    final override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == INTERFACE_TRANSACTION) {
            reply?.writeString(descriptor)
            return true
        }
        if (code != FIRST_CALL_TRANSACTION) return super.onTransact(code, data, reply, flags)
        val event = try {
            data.enforceInterface(descriptor)
            AppOpsCallbackDecoder.decode(kind, CheckedParcel(data))
        } catch (error: Exception) {
            // One diagnostic per callback registration avoids a malformed-event log flood.
            if (reportedMalformed.compareAndSet(false, true)) {
                onMalformed("$kind: ${error.message ?: error::class.java.simpleName}")
            }
            return true
        }
        event?.let(onAccess)
        return true
    }

    private class CheckedParcel(private val data: Parcel) : AppOpsCallbackInput {
        override val remaining: Int get() = data.dataAvail()
        override fun int(): Int {
            require(remaining >= 4) { "Truncated callback integer" }
            return data.readInt()
        }
        override fun string(): String? {
            val position = data.dataPosition()
            val length = int()
            require(length >= -1) { "Invalid string length" }
            val bytes = if (length == -1) 0L else ((length.toLong() + 1) * 2 + 3) and -4L
            require(bytes <= remaining.toLong()) { "Truncated callback string" }
            data.setDataPosition(position)
            return data.readString()
        }
    }
}

internal class ActiveOpCallback(
    onAccess: (AppOpAccessEvent) -> Unit,
    onMalformed: (String) -> Unit = {},
) : AppOpsWatchCallback(
    "com.android.internal.app.IAppOpsActiveCallback", AppOpAccessKind.ACTIVE, onAccess, onMalformed,
)

internal class NotedOpCallback(
    onAccess: (AppOpAccessEvent) -> Unit,
    onMalformed: (String) -> Unit = {},
) : AppOpsWatchCallback(
    "com.android.internal.app.IAppOpsNotedCallback", AppOpAccessKind.NOTED, onAccess, onMalformed,
)

internal class StartedOpCallback(
    onAccess: (AppOpAccessEvent) -> Unit,
    onMalformed: (String) -> Unit = {},
) : AppOpsWatchCallback(
    "com.android.internal.app.IAppOpsStartedCallback", AppOpAccessKind.STARTED, onAccess, onMalformed,
)
