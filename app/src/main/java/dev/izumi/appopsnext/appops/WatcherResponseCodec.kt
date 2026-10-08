package dev.izumi.appopsnext.appops

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Keeps large watcher dumps within Binder limits without discarding registrations. */
internal object WatcherResponseCodec {
    const val MAX_OUTPUT_BYTES = 16 * 1024 * 1024
    private const val MAX_TRANSPORT_CHARS = 256 * 1024
    private const val PREFIX = "APPOPS_WATCHERS_GZIP_V1\n"

    fun encode(output: String): String {
        val bytes = output.toByteArray(Charsets.UTF_8)
        check(bytes.size <= MAX_OUTPUT_BYTES) { "Watcher response exceeds the supported size" }
        if (output.length <= 64 * 1024) return output
        val buffer = ByteArrayOutputStream()
        GZIPOutputStream(buffer).use { it.write(bytes) }
        val encoded = PREFIX + Base64.getEncoder().encodeToString(buffer.toByteArray())
        check(encoded.length <= MAX_TRANSPORT_CHARS) { "Compressed watcher response exceeds the Binder limit" }
        return encoded
    }

    fun decode(output: String): String {
        if (!output.startsWith(PREFIX)) return output
        check(output.length <= MAX_TRANSPORT_CHARS) { "Compressed watcher response exceeds the Binder limit" }
        val bytes = Base64.getDecoder().decode(output.removePrefix(PREFIX))
        return GZIPInputStream(bytes.inputStream()).use { stream ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            while (true) {
                val count = stream.read(chunk)
                if (count < 0) break
                check(buffer.size() + count <= MAX_OUTPUT_BYTES) { "Watcher response exceeds the supported size" }
                buffer.write(chunk, 0, count)
            }
            buffer.toString(Charsets.UTF_8.name())
        }
    }
}
