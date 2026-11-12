package dev.izumi.appopsnext.monitor

import dev.izumi.appopsnext.appops.parser.AccessWatchKind

/** What became of one private operation's watch, as the monitor page shows it. */
enum class VendorWatchState {
    /** The system accepted the watch and its registry lists it. */
    REGISTERED,

    /** The system refused every watch for this operation. */
    REFUSED,

    /** The system accepted a watch, but its registry does not confirm it. */
    UNKNOWN,
}

/** One registration call for private operations: the codes it carried and how it ended. */
data class VendorWatchOutcome(
    val kind: AccessWatchKind,
    val codes: List<Int>,
    /** The system's reason, or null when the call was accepted. */
    val failure: String?,
)

/** The combined result of the three watches for one private operation. */
data class VendorOpWatch(
    val code: Int,
    val state: VendorWatchState,
    val acceptedKinds: Set<AccessWatchKind>,
    val confirmedKinds: Set<AccessWatchKind>,
    /** `active: <reason>` for each watch that refused the code. */
    val failures: List<String>,
)

/** One attempt of [VendorWatches.registerIsolated], with the handle a successful call returned. */
internal data class VendorWatchAttempt<H : Any>(
    val codes: List<Int>,
    val handle: H?,
    val failure: String?,
)

internal object VendorWatches {
    /**
     * Registers [codes] as one batch, and after a refused batch each code on
     * its own, so a code the system rejects costs only itself.
     *
     * The stock service checks the whole array before it registers anything,
     * so one unknown code refuses the batch. Splitting at once would take one
     * registration per code even when every code is accepted, so the batch is
     * tried first and the split costs extra calls only on a device that
     * refuses some code.
     *
     * @param attempt registers the given codes and returns the handle, or
     *   throws the system's refusal.
     */
    fun <H : Any> registerIsolated(
        codes: List<Int>,
        attempt: (IntArray) -> H,
    ): VendorRegistration<H> {
        if (codes.isEmpty()) return VendorRegistration(null, emptyList())
        val batch = tryAttempt(codes, attempt)
        if (batch.handle != null || codes.size == 1) return VendorRegistration(null, listOf(batch))
        return VendorRegistration(
            batchFailure = batch.failure,
            attempts = codes.map { tryAttempt(listOf(it), attempt) },
        )
    }

    internal data class VendorRegistration<H : Any>(
        /** Why the batch was refused before the codes were tried one by one, if it was. */
        val batchFailure: String?,
        val attempts: List<VendorWatchAttempt<H>>,
    )

    private fun <H : Any> tryAttempt(codes: List<Int>, attempt: (IntArray) -> H): VendorWatchAttempt<H> =
        try {
            VendorWatchAttempt(codes, attempt(codes.toIntArray()), null)
        } catch (error: Exception) {
            VendorWatchAttempt(codes, null, error.message ?: error::class.java.simpleName)
        }

    /**
     * Combines the watches of each code with the registry.
     *
     * @param registry the private codes the registry lists for this app's
     *   identity, by watch; a watch that is missing from the map could not be
     *   read. Null when the registry could not be read at all.
     */
    fun evaluate(
        outcomes: List<VendorWatchOutcome>,
        registry: Map<AccessWatchKind, Set<Int>>?,
    ): List<VendorOpWatch> =
        outcomes.flatMap(VendorWatchOutcome::codes).distinct().sorted().map { code ->
            val mine = outcomes.filter { code in it.codes }
            val accepted = mine.filter { it.failure == null }.mapTo(sortedSetOf()) { it.kind }
            val confirmed = accepted.filterTo(sortedSetOf()) { kind -> registry?.get(kind)?.contains(code) == true }
            VendorOpWatch(
                code = code,
                state = when {
                    accepted.isEmpty() -> VendorWatchState.REFUSED
                    confirmed.isNotEmpty() -> VendorWatchState.REGISTERED
                    else -> VendorWatchState.UNKNOWN
                },
                acceptedKinds = accepted,
                confirmedKinds = confirmed,
                failures = mine.mapNotNull { outcome ->
                    outcome.failure?.let { "${outcome.kind.label}: $it" }
                },
            )
        }

    /** The kinds that hold at least one accepted private watch. */
    fun acceptedKinds(outcomes: List<VendorWatchOutcome>): Set<AccessWatchKind> =
        outcomes.filter { it.failure == null && it.codes.isNotEmpty() }.mapTo(mutableSetOf()) { it.kind }

    /**
     * The reason a session that selected only private operations cannot start,
     * or null when one of them was accepted.
     */
    fun startFailure(outcomes: List<VendorWatchOutcome>): String? {
        if (outcomes.isEmpty() || acceptedKinds(outcomes).isNotEmpty()) return null
        val refused = evaluate(outcomes, null).joinToString(", ") { watch ->
            "${watch.code} (${watch.failures.joinToString(", ")})"
        }
        return "The system refused every selected MIUI operation: $refused"
    }

    /** One diagnostic line per code, for reports from devices the project does not have. */
    fun describe(watch: VendorOpWatch): String =
        "MIUI op ${watch.code}: ${watch.state}" +
            ", accepted=${watch.acceptedKinds.labels()}" +
            ", confirmed=${watch.confirmedKinds.labels()}" +
            if (watch.failures.isEmpty()) "" else ", refused=${watch.failures}"

    private fun Set<AccessWatchKind>.labels(): String = joinToString(",", "[", "]") { it.label }

    val AccessWatchKind.label: String get() = name.lowercase()
}

internal object MonitorSessionStatus {
    /**
     * The status a registration starts the session with.
     *
     * Private operations never decide whether a session with standard ones
     * runs: the standard watches are judged exactly as without them, and the
     * flags describe them alone. A session of private operations alone needs
     * one of them accepted, and its flags then say which watches hold one.
     * Every accepted private operation starts as unknown until the registry
     * has been read.
     *
     * @throws IllegalStateException with the reason when the session cannot run.
     */
    fun of(
        hasStandard: Boolean,
        registration: AppOpsMonitorClient.Registration,
        watchedPackages: Int,
        watchedOperations: Int,
    ): MonitorStatus {
        val status = if (hasStandard) {
            check(registration.any) {
                "No watch could be registered: ${registration.failures.joinToString()}"
            }
            MonitorStatus(
                registration.active, registration.noted, registration.started,
                registration.transactionCodesFromPlatform, watchedPackages, watchedOperations,
            )
        } else {
            VendorWatches.startFailure(registration.vendor)?.let(::error)
            val kinds = VendorWatches.acceptedKinds(registration.vendor)
            check(kinds.isNotEmpty()) { "No operations are selected" }
            MonitorStatus(
                AccessWatchKind.ACTIVE in kinds,
                AccessWatchKind.NOTED in kinds,
                AccessWatchKind.STARTED in kinds,
                registration.transactionCodesFromPlatform, watchedPackages, watchedOperations,
            )
        }
        return status.copy(vendorOps = VendorWatches.evaluate(registration.vendor, null))
    }
}
