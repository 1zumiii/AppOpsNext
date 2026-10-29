package dev.izumi.appopsnext.apps

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PackageTimesParserTest {
    private val zone: ZoneId = ZoneOffset.ofHours(8)

    private val dump = """
        Packages:
          Package [com.example.one] (33a354):
            userId=10123
            lastUpdateTime=2026-10-29 22:10:29
            User 0: ceDataInode=1 installed=true hidden=false
              firstInstallTime=2026-10-15 20:29:41
            User 10: ceDataInode=-1 installed=true hidden=false
              firstInstallTime=2026-10-20 08:00:00
          Package [com.example.two] (44b465):
            lastUpdateTime=2026-09-01 10:00:00
            User 0: ceDataInode=2 installed=true
              firstInstallTime=2026-09-01 10:00:00
            User 10: ceDataInode=-1 installed=false
              firstInstallTime=1970-01-01 08:00:00
        Hidden system packages:
          Package [com.example.one] (55c576):
            lastUpdateTime=2009-01-01 08:00:00
            User 10: ceDataInode=-1 installed=true
              firstInstallTime=2009-01-01 08:00:00
    """.trimIndent()

    private fun millis(text: String) =
        LocalDateTime.parse(text.replace(' ', 'T')).atZone(zone).toInstant().toEpochMilli()

    @Test fun `reads the first install time of the asked user and the package update time`() {
        val times = PackageTimesParser.parse(dump, userId = 10, zone = zone)
        assertEquals(millis("2026-10-20 08:00:00"), times.getValue("com.example.one").firstInstallTimeMillis)
        assertEquals(millis("2026-10-29 22:10:29"), times.getValue("com.example.one").lastUpdateTimeMillis)
    }

    @Test fun `the epoch placeholder of a user without the package reads as unknown`() {
        assertNull(PackageTimesParser.parse(dump, userId = 10, zone = zone).getValue("com.example.two").firstInstallTimeMillis)
    }

    @Test fun `a package listed again in a later section keeps its first block`() {
        assertEquals(
            millis("2026-10-29 22:10:29"),
            PackageTimesParser.parse(dump, userId = 10, zone = zone).getValue("com.example.one").lastUpdateTimeMillis,
        )
    }

    @Test fun `the compact form reads the same`() {
        val compact = PackageTimesParser.compact(dump)
        assertEquals(
            PackageTimesParser.parse(dump, userId = 10, zone = zone),
            PackageTimesParser.parse(compact, userId = 10, zone = zone),
        )
        assert(compact.length < dump.length)
    }
}
