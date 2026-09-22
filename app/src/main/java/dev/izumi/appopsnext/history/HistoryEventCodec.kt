package dev.izumi.appopsnext.history

import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.history.model.AppOpHistoryEvent
import dev.izumi.appopsnext.presentation.history.ResolvedHistoryEvent
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream

/** The event layout shared by the history snapshot and the individual-record archive. */
internal const val MAX_HISTORY_STRING_BYTES = 1 * 1024 * 1024

/**
 * The layout [writeHistoryEvent] produces. Both files record it, so a change here
 * is read back as the layout it was written in, and a newer one as unreadable.
 */
internal const val HISTORY_EVENT_LAYOUT_VERSION = 2

/**
 * @param version the layout version of the file being read; version 1 predates
 * rejection counts and interval starts.
 * @param strings repeated labels and package names are shared through this pool,
 * which matters for an archive holding many records per app.
 */
internal fun DataInputStream.readHistoryEvent(
    version: Int,
    strings: MutableMap<String, String>? = null,
): ResolvedHistoryEvent {
    fun string() = readBoundedString().let { value -> strings?.getOrPut(value) { value } ?: value }
    return ResolvedHistoryEvent(
        event = AppOpHistoryEvent(
            uid = readInt(),
            packageName = string(),
            operationName = string(),
            attributionTag = if (readBoolean()) string() else null,
            accessTimeMillis = readLong(),
            durationMillis = readNullableLong(),
            uidState = string(),
            flags = string(),
            accessCount = readInt(),
            isAggregated = readBoolean(),
            rejectCount = if (version >= 2) readBoundedCount(Int.MAX_VALUE) else 0,
            intervalStartTimeMillis = if (version >= 2) readNullableLong() else null,
        ),
        app = InstalledApp(
            label = string(),
            packageName = string(),
            uid = readInt(),
            isSystemApp = readBoolean(),
        ),
    )
}

internal fun DataOutputStream.writeHistoryEvent(resolved: ResolvedHistoryEvent) {
    val event = resolved.event
    writeInt(event.uid)
    writeBoundedString(event.packageName)
    writeBoundedString(event.operationName)
    writeNullableString(event.attributionTag)
    writeLong(event.accessTimeMillis)
    writeNullableLong(event.durationMillis)
    writeBoundedString(event.uidState)
    writeBoundedString(event.flags)
    writeInt(event.accessCount)
    writeBoolean(event.isAggregated)
    writeInt(event.rejectCount)
    writeNullableLong(event.intervalStartTimeMillis)
    val app = resolved.app
    writeBoundedString(app.label)
    writeBoundedString(app.packageName)
    writeInt(app.uid)
    writeBoolean(app.isSystemApp)
}

internal fun DataInputStream.readBoundedCount(maximum: Int): Int = readInt().also {
    if (it < 0 || it > maximum) throw IOException("Invalid history count")
}

internal fun DataInputStream.readBoundedString(): String {
    val byteCount = readBoundedCount(MAX_HISTORY_STRING_BYTES)
    val bytes = ByteArray(byteCount)
    readFully(bytes)
    return bytes.toString(Charsets.UTF_8)
}

internal fun DataOutputStream.writeBoundedString(value: String) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    if (bytes.size > MAX_HISTORY_STRING_BYTES) throw IOException("History string is too large")
    writeInt(bytes.size)
    write(bytes)
}

internal fun DataOutputStream.writeNullableString(value: String?) {
    writeBoolean(value != null)
    if (value != null) writeBoundedString(value)
}

internal fun DataInputStream.readNullableLong(): Long? = if (readBoolean()) readLong() else null

internal fun DataOutputStream.writeNullableLong(value: Long?) {
    writeBoolean(value != null)
    if (value != null) writeLong(value)
}

/** Fails the write instead of letting a file grow past what its reader accepts. */
internal class BoundedOutputStream(
    output: OutputStream,
    private val maxBytes: Long,
) : FilterOutputStream(output) {
    private var bytesWritten = 0L

    override fun write(value: Int) {
        reserve(1)
        out.write(value)
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        reserve(length)
        out.write(bytes, offset, length)
    }

    private fun reserve(count: Int) {
        bytesWritten += count
        if (bytesWritten > maxBytes) throw HistoryFileTooLargeException()
    }
}

/** A write that would pass the size its reader accepts, which a smaller write can avoid. */
internal class HistoryFileTooLargeException : IOException("History file is too large")
