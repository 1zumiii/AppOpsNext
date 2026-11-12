package dev.izumi.appopsnext.monitor

import dev.izumi.appopsnext.appops.model.AppOpNames
import dev.izumi.appopsnext.appops.model.MiuiOps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Finds out whether this device has MIUI or HyperOS private operations, from
 * what it reports for real packages rather than from the manufacturer string.
 *
 * Neither the platform's operation table nor any public call lists these
 * operations. A package's own operations do: `cmd appops get` and the binder
 * read both name them once the security center has stored a mode for them,
 * as it usually has for an ordinary app. One such entry is enough to show that the device
 * has them, so the monitor then offers the catalogued codes, plus any other
 * code a package reported.
 */
internal class VendorOpsDiscovery {
    private val mutableCodes = MutableStateFlow<Set<Int>?>(null)
    private val mutex = Mutex()
    private val readPackages = mutableSetOf<String>()

    /**
     * The private codes the monitor may try to watch. Null until a package
     * could be read, empty when the packages read so far had none.
     */
    val codes: StateFlow<Set<Int>?> = mutableCodes.asStateFlow()

    /**
     * Reads [packages] in order until one reports a private operation. A
     * package already read is not read again, so a later call with other
     * packages can still find them, and once found the answer is kept.
     *
     * @param readOperationNames the operation names of a package, or null when
     *   it could not be read.
     */
    suspend fun probe(
        packages: List<String>,
        readOperationNames: suspend (String) -> List<String>?,
    ): Set<Int> = mutex.withLock {
        mutableCodes.value?.takeIf { it.isNotEmpty() }?.let { return it }
        for (packageName in packages.distinct().filterNot(readPackages::contains).take(MAX_READS)) {
            val names = readOperationNames(packageName) ?: continue
            readPackages += packageName
            val found = names.mapNotNull(MiuiOps::codeOf)
            if (found.isNotEmpty()) {
                return offered(found).also { mutableCodes.value = it }
            }
            if (mutableCodes.value == null) mutableCodes.value = emptySet()
        }
        mutableCodes.value.orEmpty()
    }

    companion object {
        /** Reads per call, so a device without these operations costs little. */
        const val MAX_READS = 6

        /** Nothing when the device showed no private operation. */
        fun offered(observed: Collection<Int>): Set<Int> =
            if (observed.isEmpty()) emptySet() else (MiuiOps.CATALOGUED_CODES + observed).toSortedSet()
    }
}

/** The one discovery the app shares, as the operation table is shared. */
internal object VendorMonitorOps {
    val discovery = VendorOpsDiscovery()

    val codes: StateFlow<Set<Int>?> get() = discovery.codes
}

/**
 * Codes for the monitor's operation names, standard and private together.
 *
 * The monitor stores every name in the `android:` form, so a private
 * operation is kept as `android:10008`. A private name resolves only while
 * [vendorCodes] holds its code, so a device without these operations treats
 * one exactly as before: an operation it cannot watch.
 */
internal object MonitorOpCodes {
    fun isVendor(operationName: String): Boolean = MiuiOps.isMiuiOp(operationName)

    fun codeOf(
        operationName: String,
        vendorCodes: Set<Int>? = VendorMonitorOps.codes.value,
    ): Int? =
        if (isVendor(operationName)) {
            MiuiOps.codeOf(operationName)?.takeIf { it in vendorCodes.orEmpty() }
        } else {
            AppOpCodes.codeOf(operationName)
        }

    /**
     * The name an event's code is matched and recorded under. A private code
     * is named whether or not the device was probed: a callback carries one
     * only because the monitor registered it.
     */
    fun nameOf(code: Int): String? =
        AppOpCodes.nameOf(code) ?: MiuiOps.nameOf(code)?.let { vendorName(code) }

    /** The name the monitor stores a private code under, such as `android:10008`. */
    fun vendorName(code: Int): String = AppOpNames.stableName(code.toString())

    /** The picker's private entries, in code order. */
    fun vendorNames(vendorCodes: Set<Int>?): List<String> =
        vendorCodes.orEmpty().sorted().map(::vendorName)

    /** The selected operations divided by the watch they go to. */
    data class Split(
        val standard: IntArray,
        val vendor: IntArray,
        /** Names with no code on this device. */
        val unknown: List<String>,
    ) {
        override fun equals(other: Any?): Boolean =
            other is Split && standard.contentEquals(other.standard) &&
                vendor.contentEquals(other.vendor) && unknown == other.unknown

        override fun hashCode(): Int =
            (standard.contentHashCode() * 31 + vendor.contentHashCode()) * 31 + unknown.hashCode()
    }

    fun split(
        operationNames: Collection<String>,
        vendorCodes: Set<Int>? = VendorMonitorOps.codes.value,
    ): Split {
        val standard = mutableListOf<Int>()
        val vendor = mutableListOf<Int>()
        val unknown = mutableListOf<String>()
        operationNames.distinct().forEach { name ->
            val code = codeOf(name, vendorCodes)
            when {
                code == null -> unknown += name
                isVendor(name) -> vendor += code
                else -> standard += code
            }
        }
        return Split(
            standard = standard.distinct().toIntArray(),
            vendor = vendor.distinct().sorted().toIntArray(),
            unknown = unknown,
        )
    }
}
