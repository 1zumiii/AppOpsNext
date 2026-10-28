package dev.izumi.appopsnext.appops.parser

import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.history.AppOpsHistoryOutputExtractor
import dev.izumi.appopsnext.history.parser.AggregatedAppOpsHistoryParser
import dev.izumi.appopsnext.history.parser.DiscreteAppOpsHistoryParser
import java.io.File
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Runs the parsers over output captured from a real system by
 * `scripts/capture-device-dumps.sh`. Skipped unless `APPOPSNEXT_DEVICE_DUMPS`
 * names the capture directory, so ordinary unit test runs are unaffected.
 */
class DeviceDumpCompatibilityTest {
    private lateinit var dir: File
    private lateinit var capture: Properties
    private val packageName get() = capture.getProperty("package")
    private val uid get() = capture.getProperty("uid").toInt()
    private val userId get() = capture.getProperty("user").toInt()
    private val packageOp get() = capture.getProperty("packageOp")
    private val uidOp get() = capture.getProperty("uidOp")
    private val historyOp get() = capture.getProperty("historyOp")

    @Before
    fun loadCapture() {
        val path = System.getenv(DUMPS_ENV)
        assumeTrue("$DUMPS_ENV is not set", !path.isNullOrBlank())
        dir = File(checkNotNull(path))
        capture = Properties().apply { File(dir, "capture.properties").reader().use(::load) }
    }

    @Test
    fun `every command succeeded`() {
        val commands = File(dir, "commands.tsv").readLines().drop(1).map { it.split('\t') }
        val failed = commands.filter { it[1] != "0" }
        assertTrue("non-zero exit (name, exit, command): $failed", failed.isEmpty())
        // dumpsys exits 0 when the service dies during the dump.
        val lost = commands.map { it[0] }.filter { name ->
            File(dir, "$name.stderr").takeIf(File::exists)?.readText()?.let(SERVICE_LOST::containsMatchIn) == true
        }
        assertTrue("service lost during: $lost", lost.isEmpty())
    }

    @Test
    fun `package and uid reads split into scopes`() {
        val parser = PackageOpsParser()
        val uidEntries = parser.parse(packageName, dump("uid-ops")).entries
        val packageEntries = parser.parse(packageName, dump("package-ops")).entries
        assertTrue("first row carries the Uid mode prefix: $packageEntries", packageEntries.first().hasUidModePrefix)
        // AppOpsRepository splits the scopes only when the UID read is the
        // prefix of the package read: only the first UID row is prefixed.
        assertEquals(
            uidEntries.map { it.name to it.mode },
            packageEntries.take(uidEntries.size).map { it.name to it.mode },
        )
        assertTrue("UID rows: $uidEntries", uidEntries.any { it.name == uidOp && it.mode == "ignore" })
        val packageRows = packageEntries.drop(uidEntries.size)
        assertTrue("package rows: $packageRows", packageRows.any { it.name == packageOp && it.mode == "ignore" })
    }

    @Test
    fun `single operation reads`() {
        val parser = PackageOpsParser()
        val camera = parser.parse(packageName, dump("package-op-camera")).entries
        assertTrue("CAMERA rows: $camera", camera.isNotEmpty() && camera.all { it.name == "CAMERA" })
        val packageMode = parser.parse(packageName, dump("package-op-package-mode")).entries
        assertTrue(packageMode.any { it.name == packageOp && it.mode == "ignore" })
    }

    @Test
    fun `op states find the package and uid modes`() {
        for (name in listOf("op-states-camera", "op-states-package-mode", "op-states-uid-mode")) {
            assertHeader(name)
        }
        val packageStates = OpStatesParser.parse(dump("op-states-package-mode"), packageOp, userId)
        assertEquals("ignore", packageStates.packageModes[uid to packageName])
        val uidStates = OpStatesParser.parse(dump("op-states-uid-mode"), uidOp, userId)
        assertEquals("ignore", uidStates.uidModes[uid])
        assertEquals(AppOpScope.UID, uidStates.effective(uid, packageName).scope)
    }

    @Test
    fun `uid state of the package`() {
        assertHeader("uid-states")
        assertNotNull(UidStateParser.parse(dump("uid-states"))[uid])
    }

    @Test
    fun `watchers are recognized`() {
        assertHeader("watchers")
        val snapshot = AppOpsWatchersParser.parse(dump("watchers"))
        assertTrue(snapshot.recognized)
        assertTrue("malformed: ${snapshot.malformedAccessKinds}", snapshot.malformedAccessKinds.isEmpty())
        assertFalse(snapshot.malformedModeWatchers)
        assertTrue(snapshot.accessWatchers.isNotEmpty())
        assertTrue(snapshot.modeWatchers.isNotEmpty())
    }

    @Test
    fun `history finds the started access`() {
        // Both privileged backends compact the output before the parsers see it.
        val output = AppOpsHistoryOutputExtractor.extract(dump("history-camera"), historyOp)
        val discrete = DiscreteAppOpsHistoryParser().parse(historyOp, output)
        assertTrue(
            "individual records: $discrete",
            discrete.any { it.uid == uid && it.packageName == packageName },
        )
        val aggregated = AggregatedAppOpsHistoryParser().parse(historyOp, output)
        assertTrue(
            "aggregated records: $aggregated",
            aggregated.any { it.uid == uid && it.packageName == packageName },
        )
    }

    @Test
    fun `runtime permission flag`() {
        val state = RuntimePermissionStateParser()
            .parse(dump("package-permissions"), userId, capture.getProperty("permission"))
        assertNotNull("no runtime permission row for user $userId", state)
        assertTrue("REVOKED_COMPAT not read", checkNotNull(state).revokedCompat)
    }

    private fun dump(name: String): String = File(dir, "$name.txt").readText()

    private fun assertHeader(name: String) {
        assertTrue("$name.txt has no AppOps header", dump(name).lineSequence().any(AppOpsDump::isHeader))
    }

    private companion object {
        const val DUMPS_ENV = "APPOPSNEXT_DEVICE_DUMPS"

        /** Same pattern as SERVICE_LOST in scripts/capture-device-dumps.sh. */
        val SERVICE_LOST = Regex("DEAD_OBJECT|Can't find service")
    }
}
