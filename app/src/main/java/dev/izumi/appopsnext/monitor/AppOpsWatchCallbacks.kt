package dev.izumi.appopsnext.monitor

import android.os.Binder
import android.os.Parcel

/**
 * Binder stubs for the three AppOps watch callbacks.
 *
 * These are written by hand rather than generated from AIDL because the
 * interfaces live in `com.android.internal.app` and their signatures gained a
 * `virtualDeviceId` field in recent releases.
 *
 * Each parcel is parsed with the current layout first and, if that does not
 * consume the payload exactly, re-parsed without `virtualDeviceId`. Reading a
 * field that is not there would silently shift every value after it, so the
 * leftover byte count is used to tell the two layouts apart rather than trusting
 * the platform version.
 *
 * All three interfaces are `oneway`, so no reply is written.
 */
internal sealed class AppOpsWatchCallback(
    descriptor: String,
) : Binder() {
    init {
        attachInterface(null, descriptor)
    }

    protected abstract val descriptor: String

    /** @param withVirtualDeviceId whether to expect the newer field. */
    protected abstract fun readEvent(
        data: Parcel,
        withVirtualDeviceId: Boolean,
    ): AppOpAccessEvent?

    final override fun onTransact(
        code: Int,
        data: Parcel,
        reply: Parcel?,
        flags: Int,
    ): Boolean {
        if (code == INTERFACE_TRANSACTION) {
            reply?.writeString(descriptor)
            return true
        }
        if (code != FIRST_CALL_TRANSACTION) {
            return super.onTransact(code, data, reply, flags)
        }
        data.enforceInterface(descriptor)
        val start = data.dataPosition()
        // A malformed parcel must not take down the system callback thread.
        val event = parseExactly(data, start, withVirtualDeviceId = true)
            ?: parseExactly(data, start, withVirtualDeviceId = false)
        event?.let(::onEvent)
        return true
    }

    private fun parseExactly(
        data: Parcel,
        start: Int,
        withVirtualDeviceId: Boolean,
    ): AppOpAccessEvent? = runCatching {
        data.setDataPosition(start)
        val event = readEvent(data, withVirtualDeviceId)
        // The layout only matches when it accounts for the whole payload.
        if (data.dataAvail() != 0) null else event
    }.getOrNull()

    abstract fun onEvent(event: AppOpAccessEvent)
}

/**
 * `void opActiveChanged(int op, int uid, String packageName, String attributionTag,
 * int virtualDeviceId, boolean active, int attributionFlags, int attributionChainId)`
 */
internal class ActiveOpCallback(
    private val onAccess: (AppOpAccessEvent) -> Unit,
) : AppOpsWatchCallback(DESCRIPTOR) {
    override val descriptor: String = DESCRIPTOR

    override fun readEvent(
        data: Parcel,
        withVirtualDeviceId: Boolean,
    ): AppOpAccessEvent? {
        val op = data.readInt()
        val uid = data.readInt()
        val packageName = data.readString()
        data.readString() // attributionTag
        if (withVirtualDeviceId) data.readInt()
        val active = data.readInt() != 0
        data.readInt() // attributionFlags
        data.readInt() // attributionChainId
        if (packageName.isNullOrEmpty()) return null
        // Only the start of an access is reported; the matching stop would
        // otherwise raise a second, redundant notification.
        if (!active) return null
        return AppOpAccessEvent(
            opCode = op,
            uid = uid,
            packageName = packageName,
            kind = AppOpAccessKind.ACTIVE,
            allowed = true,
        )
    }

    override fun onEvent(event: AppOpAccessEvent) = onAccess(event)

    private companion object {
        const val DESCRIPTOR = "com.android.internal.app.IAppOpsActiveCallback"
    }
}

/**
 * `void opNoted(int op, int uid, String packageName, String attributionTag,
 * int virtualDeviceId, int flags, int mode)`
 */
internal class NotedOpCallback(
    private val onAccess: (AppOpAccessEvent) -> Unit,
) : AppOpsWatchCallback(DESCRIPTOR) {
    override val descriptor: String = DESCRIPTOR

    override fun readEvent(
        data: Parcel,
        withVirtualDeviceId: Boolean,
    ): AppOpAccessEvent? {
        val op = data.readInt()
        val uid = data.readInt()
        val packageName = data.readString()
        data.readString() // attributionTag
        if (withVirtualDeviceId) data.readInt()
        data.readInt() // flags
        val mode = data.readInt()
        if (packageName.isNullOrEmpty()) return null
        return AppOpAccessEvent(
            opCode = op,
            uid = uid,
            packageName = packageName,
            kind = AppOpAccessKind.NOTED,
            allowed = mode == MODE_ALLOWED,
        )
    }

    override fun onEvent(event: AppOpAccessEvent) = onAccess(event)

    private companion object {
        const val DESCRIPTOR = "com.android.internal.app.IAppOpsNotedCallback"
        const val MODE_ALLOWED = 0
    }
}

/**
 * `void opStarted(int op, int uid, String packageName, String attributionTag,
 * int virtualDeviceId, int flags, int mode, int startedType, int attributionFlags,
 * int attributionChainId)`
 *
 * This is the only callback that reports refused attempts, which is why a
 * monitor that only watched active and noted operations would miss exactly the
 * events a permission change is meant to produce.
 */
internal class StartedOpCallback(
    private val onAccess: (AppOpAccessEvent) -> Unit,
) : AppOpsWatchCallback(DESCRIPTOR) {
    override val descriptor: String = DESCRIPTOR

    override fun readEvent(
        data: Parcel,
        withVirtualDeviceId: Boolean,
    ): AppOpAccessEvent? {
        val op = data.readInt()
        val uid = data.readInt()
        val packageName = data.readString()
        data.readString() // attributionTag
        if (withVirtualDeviceId) data.readInt()
        data.readInt() // flags
        val mode = data.readInt()
        data.readInt() // startedType
        data.readInt() // attributionFlags
        data.readInt() // attributionChainId
        if (packageName.isNullOrEmpty()) return null
        val allowed = mode == MODE_ALLOWED
        // An allowed start is already reported through the active callback.
        if (allowed) return null
        return AppOpAccessEvent(
            opCode = op,
            uid = uid,
            packageName = packageName,
            kind = AppOpAccessKind.STARTED,
            allowed = false,
        )
    }

    override fun onEvent(event: AppOpAccessEvent) = onAccess(event)

    private companion object {
        const val DESCRIPTOR = "com.android.internal.app.IAppOpsStartedCallback"
        const val MODE_ALLOWED = 0
    }
}
