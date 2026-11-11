package dev.izumi.appopsnext.appops.changes

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.appops.parser.OpStates
import dev.izumi.appopsnext.apps.model.InstalledApp
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
            assertEquals(listOf("CAMERA"), synchronized(readOperations) { readOperations.toList() })

            // "Keep current value" removes the record, and the drift goes with it.
            records.value = emptyList()
            withTimeout(5_000) { monitor.drifts.first { it.isEmpty() } }
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
