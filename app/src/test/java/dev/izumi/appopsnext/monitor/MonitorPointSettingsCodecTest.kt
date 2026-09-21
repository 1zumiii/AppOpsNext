package dev.izumi.appopsnext.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorPointSettingsCodecTest {
    @Test
    fun `an absent or blank value decodes to nothing configured`() {
        assertTrue(MonitorPointSettingsCodec.decode(null).isEmpty())
        assertTrue(MonitorPointSettingsCodec.decode("   ").isEmpty())
    }

    @Test
    fun `every setting survives a round trip`() {
        val points = listOf(
            MonitorPointSettings("maps.app", "android:fine_location", throttleSeconds = 300),
            MonitorPointSettings("cam.app", "android:camera", headsUp = true),
            MonitorPointSettings("quiet.app", "android:camera", headsUp = false),
            MonitorPointSettings(
                "mixed.app",
                "android:read_sms",
                throttleSeconds = 90,
                headsUp = true,
                outcomes = MonitorOutcomes.REFUSED,
                backgroundOnly = true,
            ),
            MonitorPointSettings("bg.app", "android:camera", backgroundOnly = true),
        )
        val decoded = MonitorPointSettingsCodec.decode(MonitorPointSettingsCodec.encode(points))
        assertEquals(points.toSet(), decoded.toSet())
    }

    /** A point that chose nothing is not worth a line. */
    @Test
    fun `defaults are never written or read back`() {
        val defaults = MonitorPointSettings("app", "android:camera")
        assertTrue(defaults.isDefault)
        assertEquals("", MonitorPointSettingsCodec.encode(listOf(defaults)))
        assertTrue(MonitorPointSettingsCodec.decode("app android:camera outcome=all").isEmpty())
    }

    /** An earlier build wrote the interval as a bare third field. */
    @Test
    fun `a bare number still reads as an interval`() {
        val decoded = MonitorPointSettingsCodec.decode("app android:camera 45").single()
        assertEquals(45, decoded.throttleSeconds)
        assertNull(decoded.headsUp)
        assertEquals(MonitorOutcomes.ALL, decoded.outcomes)
    }

    /** One unreadable field must not discard the settings beside it. */
    @Test
    fun `an unreadable field is skipped rather than failing the line`() {
        val decoded = MonitorPointSettingsCodec
            .decode("app android:camera throttle=abc headsup=maybe outcome=refused")
            .single()
        assertNull(decoded.throttleSeconds)
        assertNull(decoded.headsUp)
        assertEquals(MonitorOutcomes.REFUSED, decoded.outcomes)
    }

    @Test
    fun `an interval outside the allowed range is dropped`() {
        assertTrue(MonitorPointSettingsCodec.decode("app android:camera throttle=0").isEmpty())
        assertTrue(MonitorPointSettingsCodec.decode("app android:camera throttle=-5").isEmpty())
        assertTrue(MonitorPointSettingsCodec.decode("app android:camera throttle=86401").isEmpty())
        assertEquals(
            86400,
            MonitorPointSettingsCodec.decode("app android:camera throttle=86400")
                .single().throttleSeconds,
        )
    }

    @Test
    fun `malformed lines are dropped rather than guessed at`() {
        assertTrue(MonitorPointSettingsCodec.decode("only.package").isEmpty())
        assertTrue(MonitorPointSettingsCodec.decode("pkg android:camera").isEmpty())
    }

    @Test
    fun `one point keeps one set of settings`() {
        val decoded = MonitorPointSettingsCodec.decode(
            "app android:camera throttle=30\napp android:camera throttle=60",
        )
        assertEquals(1, decoded.size)
        assertEquals(30, decoded.single().throttleSeconds)
    }

    @Test
    fun `an outcome decides which accesses are reported`() {
        assertTrue(MonitorOutcomes.ALL.reports(true))
        assertTrue(MonitorOutcomes.ALL.reports(false))
        assertTrue(MonitorOutcomes.REFUSED.reports(false))
        assertFalse(MonitorOutcomes.REFUSED.reports(true))
        assertTrue(MonitorOutcomes.ALLOWED.reports(true))
        assertFalse(MonitorOutcomes.ALLOWED.reports(false))
    }

    @Test
    fun `a point falls back to the monitor setting only when it chose nothing`() {
        val follows = MonitorPointSettings("a", "android:camera")
        assertTrue(follows.headsUp(default = true))
        assertFalse(follows.headsUp(default = false))
        assertTrue(follows.copy(headsUp = true).headsUp(default = false))
        assertFalse(follows.copy(headsUp = false).headsUp(default = true))
    }

    /** A line written before this setting existed reads as its default. */
    @Test
    fun `an older line has the new setting off`() {
        val decoded = MonitorPointSettingsCodec
            .decode("app android:camera throttle=45 headsup=on")
            .single()
        assertFalse(decoded.backgroundOnly)
        assertEquals(45, decoded.throttleSeconds)
    }

    @Test
    fun `millis follows the stored seconds`() {
        assertEquals(
            300_000L,
            MonitorPointSettings("a", "android:camera", throttleSeconds = 300).throttleMillis,
        )
        assertNull(MonitorPointSettings("a", "android:camera").throttleMillis)
    }
}
