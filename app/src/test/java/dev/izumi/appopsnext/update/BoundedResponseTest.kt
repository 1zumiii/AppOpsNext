package dev.izumi.appopsnext.update

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class BoundedResponseTest {
    @Test fun `accepts exactly the limit and decodes utf8`() {
        val body = "更新".toByteArray(Charsets.UTF_8)
        assertEquals("更新", ByteArrayInputStream(body).readBoundedResponse(body.size))
        assertTrue(runCatching { ByteArrayInputStream(body).readBoundedResponse(body.size - 1) }
            .exceptionOrNull() is IOException)
    }

    @Test fun `unbounded stream is stopped after one sentinel byte`() {
        var read = 0
        val stream = object : InputStream() {
            override fun read(): Int { read++; return 65 }
        }
        assertTrue(runCatching { stream.readBoundedResponse(512 * 1024) }
            .exceptionOrNull() is IOException)
        assertEquals(512 * 1024 + 1, read)
    }

    @Test fun `empty and short chunked responses work`() {
        assertEquals("", ByteArrayInputStream(byteArrayOf()).readBoundedResponse(0))
        val stream = object : ByteArrayInputStream("abcdef".toByteArray()) {
            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, minOf(1, len))
        }
        assertEquals("abcdef", stream.readBoundedResponse(6))
    }
}
