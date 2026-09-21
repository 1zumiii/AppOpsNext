package dev.izumi.appopsnext.update

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.IOException

/** Reads at most limit + one sentinel byte; never accumulates an oversized response. */
internal fun InputStream.readBoundedResponse(limit: Int): String {
    require(limit >= 0)
    val output = ByteArrayOutputStream(minOf(limit, 8192))
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer, 0, minOf(buffer.size, limit - output.size() + 1))
        if (count == -1) return output.toString(Charsets.UTF_8.name())
        if (count > limit - output.size()) throw IOException("Update response exceeds $limit bytes")
        output.write(buffer, 0, count)
    }
}
