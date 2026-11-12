package dev.izumi.appopsnext.appops.changes

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.appops.parser.OpStates
import dev.izumi.appopsnext.apps.model.InstalledApp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOpChangeDriftsTest {
    private val camera = "android:camera"
    private val app = installed("com.example.app", uid = 10_100)
    private val apps = mapOf(0 to mapOf(app.packageName to app))

    @Test
    fun `a mode that differs from the recorded one is a drift`() {
        val records = listOf(record(app.packageName, camera, AppOpMode.IGNORE))
        val states = mapOf(AppOpChangeRead(0, camera) to uidStates(app.uid to "allow"))

        val drifts = AppOpChangeDrifts.find(records, apps, states)

        assertEquals(1, drifts.size)
        assertEquals(app, drifts.single().app)
        assertEquals(AppOpMode.ALLOW, drifts.single().status.currentMode)
        assertEquals(AppOpMode.IGNORE, drifts.single().status.record.recordedMode)
    }

    @Test
    fun `a mode still as recorded is not a drift`() {
        val records = listOf(record(app.packageName, camera, AppOpMode.IGNORE))
        val states = mapOf(AppOpChangeRead(0, camera) to uidStates(app.uid to "ignore"))

        assertTrue(AppOpChangeDrifts.find(records, apps, states).isEmpty())
    }

    @Test
    fun `a missing record on the device reads as the default mode`() {
        val records = listOf(record(app.packageName, camera, AppOpMode.IGNORE))
        val states = mapOf(AppOpChangeRead(0, camera) to OpStates(emptyMap(), emptyMap()))

        assertEquals(
            AppOpMode.DEFAULT,
            AppOpChangeDrifts.find(records, apps, states).single().status.currentMode,
        )
    }

    @Test
    fun `only the operations and apps in the log are read`() {
        val other = installed("com.example.other", uid = 10_200)
        val records = listOf(
            record(app.packageName, camera, AppOpMode.IGNORE),
            record(other.packageName, camera, AppOpMode.IGNORE),
            record("com.example.gone", "android:record_audio", AppOpMode.IGNORE),
        )
        val installed = mapOf(0 to mapOf(app.packageName to app, other.packageName to other))

        assertEquals(
            mapOf(AppOpChangeRead(0, camera) to setOf(app.uid, other.uid)),
            AppOpChangeDrifts.reads(records, installed),
        )
    }

    @Test
    fun `an app installed again after the change is left out`() {
        val reinstalled = app.copy(firstInstallTimeMillis = 2_000)
        val records = listOf(record(app.packageName, camera, AppOpMode.IGNORE, timeMillis = 1_000))
        val installed = mapOf(0 to mapOf(app.packageName to reinstalled))
        val states = mapOf(AppOpChangeRead(0, camera) to uidStates(app.uid to "allow"))

        assertTrue(AppOpChangeDrifts.reads(records, installed).isEmpty())
        assertTrue(AppOpChangeDrifts.find(records, installed, states).isEmpty())
    }

    @Test
    fun `a failed read leaves its settings out`() {
        val records = listOf(record(app.packageName, camera, AppOpMode.IGNORE))

        assertTrue(AppOpChangeDrifts.find(records, apps, emptyMap()).isEmpty())
    }

    @Test
    fun `each user is compared with its own apps and modes`() {
        val clone = installed("com.example.app", uid = 1_010_100, userId = 10)
        val records = listOf(
            record(app.packageName, camera, AppOpMode.IGNORE, userId = 0),
            record(app.packageName, camera, AppOpMode.IGNORE, userId = 10),
        )
        val installed = apps + (10 to mapOf(clone.packageName to clone))
        val states = mapOf(
            AppOpChangeRead(0, camera) to uidStates(app.uid to "ignore"),
            AppOpChangeRead(10, camera) to uidStates(clone.uid to "allow"),
        )

        assertEquals(listOf(clone), AppOpChangeDrifts.find(records, installed, states).map { it.app })
    }

    @Test
    fun `the monitor reads once connected and again when the log changes`(): Unit = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        val records = MutableStateFlow(listOf(record(app.packageName, camera, AppOpMode.IGNORE)))
        val connected = MutableStateFlow(false)
        val readOperations = mutableListOf<String>()
        val monitor = AppOpChangeDriftMonitor(
            records = records,
            connected = connected,
            loadApps = { listOf(app) },
            loadOpStates = { operation, _, _ ->
                synchronized(readOperations) { readOperations += operation }
                uidStates(app.uid to "allow")
            },
            scope = scope,
            settleMillis = 0,
        )
        try {
            connected.value = true
            withTimeout(5_000) { monitor.drifts.first { it.size == 1 } }
            // The change found is read once more before it is flagged, and nothing else is read.
            assertEquals(setOf("CAMERA"), synchronized(readOperations) { readOperations.toSet() })

            // "Keep current value" removes the record, and the drift goes with it.
            records.value = emptyList()
            withTimeout(5_000) { monitor.drifts.first { it.isEmpty() } }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `only records not yet flagged that differ on the device are newly changed`() {
        val differs = record(app.packageName, camera, AppOpMode.IGNORE)
        val flagged = record(app.packageName, camera, AppOpMode.IGNORE, timeMillis = 900)
            .copy(scope = AppOpScope.PACKAGE, changedElsewhere = true)
        val same = record(app.packageName, "android:record_audio", AppOpMode.IGNORE)
        val states = mapOf(
            AppOpChangeRead(0, camera) to uidStates(app.uid to "allow"),
            AppOpChangeRead(0, "android:record_audio") to uidStates(app.uid to "ignore"),
        )

        assertEquals(
            listOf(differs),
            AppOpChangeDrifts.newlyChanged(listOf(differs, flagged, same), apps, states),
        )
    }

    @Test
    fun `a flagged setting back at the recorded mode is no drift and one still changed is`() {
        val flagged = record(app.packageName, camera, AppOpMode.IGNORE).copy(changedElsewhere = true)

        val back = mapOf(AppOpChangeRead(0, camera) to uidStates(app.uid to "ignore"))
        val still = mapOf(AppOpChangeRead(0, camera) to uidStates(app.uid to "foreground"))

        assertTrue(AppOpChangeDrifts.find(listOf(flagged), apps, back).isEmpty())
        assertEquals(1, AppOpChangeDrifts.find(listOf(flagged), apps, still).size)
    }

    @Test
    fun `the monitor flags a change it reads again between writes`(): Unit = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        val original = record(app.packageName, camera, AppOpMode.IGNORE)
        val records = MutableStateFlow(listOf(original))
        val marked = mutableListOf<AppOpChangeRecord>()
        var betweenWrites = 0
        val monitor = AppOpChangeDriftMonitor(
            records = records,
            connected = MutableStateFlow(true),
            loadApps = { listOf(app) },
            loadOpStates = { _, _, _ -> uidStates(app.uid to "foreground") },
            markChangedElsewhere = { seen ->
                synchronized(marked) { marked += seen }
                records.value = AppOpChangeRecords.markChangedElsewhere(records.value, seen)
            },
            betweenWrites = { action ->
                synchronized(marked) { betweenWrites++ }
                action()
            },
            scope = scope,
            settleMillis = 0,
        )
        try {
            withTimeout(5_000) { records.first { it.single().changedElsewhere } }
            withTimeout(5_000) { monitor.drifts.first { it.singleOrNull()?.status?.record?.changedElsewhere == true } }
            synchronized(marked) {
                assertEquals(listOf(original), marked)
                assertEquals(1, betweenWrites)
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `the monitor does not flag a mode that is back once writes are done`(): Unit = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        val records = MutableStateFlow(listOf(record(app.packageName, camera, AppOpMode.IGNORE)))
        val marked = mutableListOf<AppOpChangeRecord>()
        val confirmed = CompletableDeferred<Unit>()
        var writing = true
        val monitor = AppOpChangeDriftMonitor(
            records = records,
            connected = MutableStateFlow(true),
            loadApps = { listOf(app) },
            // A write of this app in progress shows another mode until it finishes or rolls back.
            loadOpStates = { _, _, _ -> uidStates(app.uid to if (writing) "allow" else "ignore") },
            markChangedElsewhere = { seen -> synchronized(marked) { marked += seen } },
            betweenWrites = { action ->
                writing = false
                action()
                confirmed.complete(Unit)
            },
            scope = scope,
            settleMillis = 0,
        )
        try {
            withTimeout(5_000) { confirmed.await() }
            assertEquals(1, monitor.drifts.value.size)
            assertTrue(synchronized(marked) { marked.isEmpty() })
        } finally {
            scope.cancel()
        }
    }

    private fun installed(packageName: String, uid: Int, userId: Int? = null) = InstalledApp(
        label = packageName,
        packageName = packageName,
        uid = uid,
        isSystemApp = false,
        userId = userId,
        firstInstallTimeMillis = 500,
    )

    private fun record(
        packageName: String,
        operation: String,
        mode: AppOpMode,
        userId: Int = 0,
        timeMillis: Long = 1_000,
    ) = AppOpChangeRecord(
        userId = userId,
        packageName = packageName,
        stableOperationName = operation,
        scope = AppOpScope.UID,
        originalMode = AppOpMode.ALLOW,
        recordedMode = mode,
        timeMillis = timeMillis,
    )

    private fun uidStates(vararg modes: Pair<Int, String>) = OpStates(mapOf(*modes), emptyMap())
}
