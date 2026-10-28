package dev.izumi.appopsnext.appops

import android.os.SystemClock
import dev.izumi.appopsnext.appops.binder.DeviceOp
import dev.izumi.appopsnext.appops.model.PackageOpsSnapshot
import dev.izumi.appopsnext.appops.parser.OpStates
import dev.izumi.appopsnext.monitor.AppOpCodes
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** What the probe enabled, and a short reason for each capability it left off. */
data class AppOpsReadProbeResult(
    val capabilities: Set<AppOpsReadCapability>,
    val notes: List<String>,
)

/** The checks a binder read must pass before it replaces a text read. */
internal object AppOpsReadComparison {
    /** Same operations, modes and scopes; usage times differ between two reads. */
    fun samePackageOps(text: PackageOpsSnapshot, binder: PackageOpsSnapshot): Boolean =
        keys(text) == keys(binder)

    fun sameEffectiveStates(
        text: OpStates,
        binder: OpStates,
        apps: List<Pair<Int, String>>,
    ): Boolean = apps.all { (uid, packageName) ->
        text.effective(uid, packageName) == binder.effective(uid, packageName)
    }

    /**
     * The device table answers for this device even where it differs from the
     * AOSP order, so differences are reported but do not reject it. Only a table
     * that cannot be right is rejected: too short, or names that repeat.
     */
    fun opTableUsable(device: List<DeviceOp>, builtIn: Map<Int, String>): Boolean =
        device.size >= builtIn.size / 2 &&
            device.map { it.shellName }.toSet().size == device.size &&
            device.all { it.publicName.startsWith("android:") }

    fun opTableDifferences(device: List<DeviceOp>, builtIn: Map<Int, String>): List<Int> {
        val byCode = device.associateBy { it.code }
        return builtIn.filter { (code, name) -> byCode[code]?.publicName != name }.keys.sorted()
    }

    private fun keys(snapshot: PackageOpsSnapshot) =
        snapshot.entries
            .map { Triple(it.name.uppercase(Locale.ROOT), it.mode, it.scope) }
            .groupingBy { it }
            .eachCount()
}

/**
 * Reads the same state through both paths and enables a binder read only where
 * the two agree. Text stays the reference because it is what every release so
 * far has been tested against.
 */
class AppOpsReadProbe(
    private val structured: StructuredAppOpsReads,
    private val textPackageOps: suspend (packageName: String, uid: Int) -> PackageOpsSnapshot?,
    private val textOpStates: suspend (operation: String, userId: Int) -> OpStates?,
    private val elapsedMillis: () -> Long = SystemClock::elapsedRealtime,
) {
    /** [apps] are the current user's installed apps as UID and package, [samples] the packages compared in full. */
    suspend fun run(
        userId: Int,
        apps: List<Pair<Int, String>>,
        samples: List<Pair<Int, String>>,
    ): AppOpsReadProbeResult {
        val capabilities = mutableSetOf<AppOpsReadCapability>()
        val notes = mutableListOf<String>()
        val table = structured.opTableForProbe()
        if (table == null) {
            notes += "opList=unavailable"
        } else {
            val differences = AppOpsReadComparison.opTableDifferences(table, AppOpCodes.builtIn)
            if (AppOpsReadComparison.opTableUsable(table, AppOpCodes.builtIn)) {
                capabilities += AppOpsReadCapability.OP_LIST
            } else {
                notes += "opList=rejected"
            }
            notes += "opCount=${table.size}"
            if (differences.isNotEmpty()) notes += "opDiffers=${differences.take(MAX_LISTED)}"
        }
        // Without the device's names a binder record cannot be named at all.
        if (table == null) return AppOpsReadProbeResult(capabilities, notes)

        check("packageOps", AppOpsReadCapability.PACKAGE_OPS, capabilities, notes) {
            samples.all { (uid, packageName) ->
                val text = textPackageOps(packageName, uid) ?: error("text read failed")
                val binder = structured.packageOps(packageName, uid, force = true)
                    ?: error("binder read returned nothing")
                AppOpsReadComparison.samePackageOps(text, binder)
            }
        }
        check("opStates", AppOpsReadCapability.OP_STATES, capabilities, notes) {
            val text = textOpStates(PROBE_OPERATION, userId) ?: error("text read failed")
            val started = elapsedMillis()
            val binder = structured.opStates(PROBE_OPERATION, userId, apps.map { it.first }, force = true)
                ?: error("binder read returned nothing")
            notes += "opStatesMillis=${elapsedMillis() - started}"
            AppOpsReadComparison.sameEffectiveStates(text, binder, apps)
        }
        return AppOpsReadProbeResult(capabilities, notes)
    }

    /** One retry, because an app can touch an operation between the two reads. */
    private suspend fun check(
        label: String,
        capability: AppOpsReadCapability,
        capabilities: MutableSet<AppOpsReadCapability>,
        notes: MutableList<String>,
        agrees: suspend () -> Boolean,
    ) {
        repeat(ATTEMPTS) { attempt ->
            val outcome = try {
                agrees()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (error is VirtualMachineError) throw error
                notes += "$label=error(${error::class.java.simpleName}: ${error.message?.take(MAX_MESSAGE)})"
                return
            }
            if (outcome) {
                capabilities += capability
                return
            }
            if (attempt < ATTEMPTS - 1) delay(RETRY_DELAY_MILLIS)
        }
        notes += "$label=mismatch"
    }

    private companion object {
        /** Present on every supported release and usually set for many apps. */
        const val PROBE_OPERATION = "CAMERA"
        const val ATTEMPTS = 2
        const val RETRY_DELAY_MILLIS = 500L
        const val MAX_LISTED = 8
        const val MAX_MESSAGE = 120
    }
}
