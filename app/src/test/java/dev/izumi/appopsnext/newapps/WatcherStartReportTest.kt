package dev.izumi.appopsnext.newapps

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatcherStartReportTest {
    private val zone = ZoneOffset.UTC

    @Test fun `a report survives encoding`() {
        val report = WatcherStartReport(
            pid = 4321,
            uid = 2000,
            startEpochMillis = 1_700_000_000_000L,
            stale = listOf(
                WatcherStartReport.StaleWatcher(1234, 1_699_000_000_000L, null),
                WatcherStartReport.StaleWatcher(1300, null, "EPERM"),
            ),
        )

        assertEquals(report, WatcherStartReport.decode(WatcherStartReport.encode(report)))
    }

    @Test fun `a failed scan survives encoding with its message as one word`() {
        val report = WatcherStartReport(4321, 0, null, stale = null, scanError = "SecurityException: no access")

        val decoded = WatcherStartReport.decode(WatcherStartReport.encode(report))!!

        assertNull(decoded.stale)
        assertEquals("SecurityException:_no_access", decoded.scanError)
        assertEquals(
            "Install watcher stale check at start failed. error=SecurityException:_no_access",
            decoded.staleLine(zone),
        )
    }

    @Test fun `text that is not a report decodes to nothing`() {
        assertNull(WatcherStartReport.decode(null))
        assertNull(WatcherStartReport.decode(""))
        assertNull(WatcherStartReport.decode("v2 1 2 3"))
        assertNull(WatcherStartReport.decode("v1 1 2 3\nsomething else"))
    }

    @Test fun `an old watcher that cannot report costs only the stale line`() {
        assertEquals(
            listOf("Install watcher running. pid=4321, connectMs=850, startReport=unsupported"),
            WatcherStartReport.connectLines(4321, reportText = null, connectMillis = 850, zone = zone),
        )
    }

    @Test fun `a report from another process is not trusted`() {
        val other = WatcherStartReport.encode(WatcherStartReport(99, 2000, 0, emptyList()))

        assertEquals(
            listOf("Install watcher running. pid=4321, startReport=unsupported"),
            WatcherStartReport.connectLines(4321, other, connectMillis = null, zone = zone),
        )
    }

    @Test fun `a new watcher reports itself and says when nothing was left over`() {
        val text = WatcherStartReport.encode(WatcherStartReport(4321, 2000, 1_700_000_000_000L, emptyList()))

        assertEquals(
            listOf(
                "Install watcher running. pid=4321, uid=2000, started=2023-11-14T22:13:20Z, connectMs=850",
                "Install watcher stale check at start. found=0",
            ),
            WatcherStartReport.connectLines(4321, text, 850, zone),
        )
    }

    @Test fun `ended stale watchers are listed with their start and the result`() {
        val text = WatcherStartReport.encode(
            WatcherStartReport(
                4321, 2000, 1_700_000_000_000L,
                listOf(
                    WatcherStartReport.StaleWatcher(1234, 1_699_222_400_000L, null),
                    WatcherStartReport.StaleWatcher(1300, null, "EPERM"),
                ),
            ),
        )

        val lines = WatcherStartReport.connectLines(4321, text, 850, zone)

        assertEquals(
            "Install watcher stale check at start. found=2, processes=[" +
                "pid=1234 started=2023-11-05T22:13:20Z result=ended, " +
                "pid=1300 started=unknown result=failed(EPERM)]",
            lines[1],
        )
    }

    @Test fun `a reconnect to the same watcher does not repeat its stale check`() {
        val text = WatcherStartReport.encode(WatcherStartReport(4321, 2000, 1_700_000_000_000L, emptyList()))

        val lines = WatcherStartReport.connectLines(4321, text, 10, zone, staleAlreadyLogged = { true })

        assertEquals(1, lines.size)
    }
}
