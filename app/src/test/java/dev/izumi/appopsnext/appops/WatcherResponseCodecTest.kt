package dev.izumi.appopsnext.appops

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatcherResponseCodecTest {
    @Test fun `legacy plaintext remains readable`() {
        val text = "Current AppOps Service state:\n  All op mode watchers:\n"
        assertEquals(text, WatcherResponseCodec.decode(text))
        assertEquals(text, WatcherResponseCodec.encode(text))
    }

    @Test fun `large multilingual dump crosses Binder in compressed form without loss`() {
        val text = "Current AppOps Service state:\n" + "  Package 测试.camera:\n".repeat(50000)
        val encoded = WatcherResponseCodec.encode(text)
        assertTrue(text.toByteArray().size > 512 * 1024)
        assertTrue(encoded.length < 256 * 1024)
        assertEquals(text, WatcherResponseCodec.decode(encoded))
    }

    @Test(expected = IllegalStateException::class)
    fun `oversized raw dump is rejected`() {
        WatcherResponseCodec.encode("a".repeat(WatcherResponseCodec.MAX_OUTPUT_BYTES + 1))
    }

    @Test(expected = IllegalStateException::class)
    fun `compressed response cannot expand beyond the output limit`() {
        val buffer = ByteArrayOutputStream()
        GZIPOutputStream(buffer).use { it.write(ByteArray(WatcherResponseCodec.MAX_OUTPUT_BYTES + 1)) }
        WatcherResponseCodec.decode("APPOPS_WATCHERS_GZIP_V1\n" + Base64.getEncoder().encodeToString(buffer.toByteArray()))
    }
}
