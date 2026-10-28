package dev.izumi.appopsnext.appops

import android.content.pm.PackageManager
import dev.izumi.appopsnext.appops.binder.BinderAppOpsReader
import dev.izumi.appopsnext.appops.binder.DeviceOp
import dev.izumi.appopsnext.appops.binder.PlatformOpNames
import dev.izumi.appopsnext.appops.binder.StructuredOpsMapper
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpNames
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.appops.model.PackageOpsSnapshot
import dev.izumi.appopsnext.appops.parser.OpStates
import dev.izumi.appopsnext.monitor.AppOpCodes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** Reads that can come from `IAppOpsService` instead of parsed command output. */
enum class AppOpsReadCapability {
    /** `cmd appops get <package>` and `cmd appops get <package> <op>`. */
    PACKAGE_OPS,

    /** `dumpsys appops --op <op>`, the by-permission view. */
    OP_STATES,

    /** The operation list, which has no text source at all. */
    OP_LIST,
}

/**
 * Binder reads, used only for the capabilities [AppOpsReadProbe] confirmed on
 * this build.
 *
 * Every read returns null when its capability is off or the call fails, and
 * the caller then runs the text command. A failure also turns the capability
 * off for this process and reports it, so the next connection probes again.
 */
class StructuredAppOpsReads internal constructor(
    private val uidOf: (String) -> Int?,
    private val reader: BinderAppOpsReader,
    private val loadOpTable: () -> List<DeviceOp>?,
    private val onFailure: (AppOpsReadCapability, Throwable) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    constructor(
        packageManager: PackageManager,
        onFailure: (AppOpsReadCapability, Throwable) -> Unit,
    ) : this(
        uidOf = { packageName ->
            kotlin.runCatching {
                packageManager.getPackageUid(packageName, PackageManager.PackageInfoFlags.of(0))
            }.getOrNull()
        },
        reader = BinderAppOpsReader(),
        loadOpTable = PlatformOpNames::read,
        onFailure = onFailure,
    )

    private val mutableEnabled = MutableStateFlow<Set<AppOpsReadCapability>>(emptySet())
    val enabled: StateFlow<Set<AppOpsReadCapability>> = mutableEnabled.asStateFlow()

    private val opTable by lazy { loadOpTable() }
    private val nameByCode by lazy { opTable?.associate { it.code to it.shellName } }
    private val codeByName by lazy { opTable?.associate { it.shellName to it.code } }

    /** The entries of [opTable] that can be addressed by name; binder records still use the whole table. */
    private val listedOps by lazy {
        opTable?.let { AppOpsReadComparison.usableOpTable(it, AppOpCodes.builtIn)?.ops }
    }

    fun enable(capabilities: Set<AppOpsReadCapability>) {
        mutableEnabled.value = capabilities
        AppOpCodes.useDeviceTable(listedOps.takeIf { AppOpsReadCapability.OP_LIST in capabilities })
    }

    /** The platform's operation table, or null to use the built-in one. */
    fun deviceOps(): List<DeviceOp>? =
        listedOps.takeIf { AppOpsReadCapability.OP_LIST in mutableEnabled.value }

    internal fun opTableForProbe(): List<DeviceOp>? = opTable

    suspend fun packageOps(
        packageName: String,
        uid: Int?,
        force: Boolean = false,
    ): PackageOpsSnapshot? = attempt(AppOpsReadCapability.PACKAGE_OPS, force) {
        val appUid = uid ?: uidOf(packageName) ?: error("Unknown package $packageName")
        val names = checkNotNull(nameByCode) { "Operation names are unavailable" }
        reader.session {
            StructuredOpsMapper.packageSnapshot(
                packageName = packageName,
                uidOps = uidOps(appUid),
                packageOps = opsForPackage(appUid, packageName),
                nameOf = names::get,
                nowMillis = clock(),
            )
        } ?: error("Unnamed operation or mode in $packageName")
    }

    suspend fun mode(
        packageName: String,
        operationName: String,
        scope: AppOpScope,
        force: Boolean = false,
    ): AppOpMode? = attempt(AppOpsReadCapability.PACKAGE_OPS, force) {
        val uid = uidOf(packageName) ?: error("Unknown package $packageName")
        val code = codeOf(operationName)
        val ops = intArrayOf(code)
        val records = reader.session {
            when (scope) {
                AppOpScope.UID -> uidOps(uid, ops)
                AppOpScope.PACKAGE -> opsForPackage(uid, packageName, ops)
            }
        }
        StructuredOpsMapper.mode(records, code) ?: error("Unknown mode for $operationName")
    }

    /** UID modes are per UID calls, so [uids] should be the apps the caller shows. */
    suspend fun opStates(
        operationName: String,
        userId: Int,
        uids: Collection<Int>,
        force: Boolean = false,
    ): OpStates? = attempt(AppOpsReadCapability.OP_STATES, force) {
        val code = codeOf(operationName)
        val ops = intArrayOf(code)
        reader.session {
            StructuredOpsMapper.opStates(
                code = code,
                userId = userId,
                packages = packagesForOps(ops),
                uidRecords = uids.distinct().flatMap { uidOps(it, ops) },
            )
        } ?: error("Unknown mode for $operationName")
    }

    private fun codeOf(operationName: String): Int =
        checkNotNull(codeByName?.get(AppOpNames.shellName(operationName))) {
            "Unknown operation $operationName"
        }

    /** With [force], used by the probe, a failure is thrown instead of absorbed. */
    private suspend fun <T> attempt(
        capability: AppOpsReadCapability,
        force: Boolean,
        block: () -> T,
    ): T? {
        if (!force && capability !in mutableEnabled.value) return null
        return withContext(Dispatchers.IO) {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                // Reflection on a changed platform throws errors, not only exceptions.
                if (force || error is VirtualMachineError) throw error
                mutableEnabled.update { it - capability }
                onFailure(capability, error)
                null
            }
        }
    }
}
