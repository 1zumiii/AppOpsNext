package dev.izumi.appopsnext.appops.parser

/** A snapshot of registrations, not evidence that a callback was delivered. */
data class AppOpsWatcherSnapshot(
    val recognized: Boolean,
    val accessWatchers: List<AccessWatcher>,
    val modeWatchers: List<ModeWatcher>,
    val malformedAccessKinds: Set<AccessWatchKind>,
    val malformedModeWatchers: Boolean,
) {
    fun registration(operations: Set<String>, kinds: Set<AccessWatchKind>, callerUid: Int): WatchRegistration {
        if (!recognized || operations.isEmpty() || kinds.isEmpty() || callerUid < 0 ||
            kinds.any { it in malformedAccessKinds }
        ) return WatchRegistration.UNKNOWN
        // Shizuku forwards registrations under its own identity. Another client
        // may own a matching row: combine this evidence with our successful
        // registration calls, never claim unique ownership or end-to-end delivery.
        return if (kinds.all { kind ->
                accessWatchers.any {
                    it.kind == kind && it.callerUid == callerUid && it.watchingUid == -1 &&
                        it.operations.containsAll(operations)
                }
            }
        ) WatchRegistration.CONFIRMED else WatchRegistration.MISSING
    }
}

enum class WatchRegistration { CONFIRMED, MISSING, UNKNOWN }
enum class AccessWatchKind { ACTIVE, STARTED, NOTED }

data class AccessWatcher(
    val kind: AccessWatchKind,
    val operations: Set<String>,
    val watchingUid: Int,
    val callerUid: Int,
    val callerPid: Int,
)

data class ModeWatcher(
    val callbackId: String,
    val watchingUid: Int,
    val callerUid: Int,
    val callerPid: Int,
    val reportedOperation: String,
    /** Registration indices are additive; a package entry does not narrow an op entry. */
    val operations: Set<String> = emptySet(),
    val packages: Set<String> = emptySet(),
)

object AppOpsWatchersParser {
    fun parse(output: String): AppOpsWatcherSnapshot {
        val access = mutableListOf<AccessWatcher>()
        val modes = linkedMapOf<String, ModeWatcher>()
        val malformed = mutableSetOf<AccessWatchKind>()
        var malformedModes = false
        var section: String? = null
        var kind: AccessWatchKind? = null
        var operations: Set<String>? = null
        var entryPending = false
        var modeOp: String? = null
        var modePackage: String? = null
        var recognizedSection = false
        fun endEntry() {
            if (entryPending) kind?.let(malformed::add)
            operations = null
            entryPending = false
        }
        for (raw in output.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (raw.startsWith("  ") && !raw.startsWith("   ")) {
                endEntry()
                section = line.takeIf { it in SECTIONS }
                if (section != null) recognizedSection = true
                kind = ACCESS_SECTIONS[section]
                modeOp = null
                modePackage = null
                continue
            }
            val accessKind = kind
            if (accessKind != null) {
                when {
                    ENTRY.matches(line) -> {
                        endEntry()
                        entryPending = true
                    }
                    line.startsWith("[") && line.endsWith("]") && entryPending -> {
                        val names = line.drop(1).dropLast(1).split(',').map(String::trim)
                        operations = names.takeIf { it.isNotEmpty() && it.all(OP::matches) }?.toSet()
                        if (operations == null) malformed += accessKind
                    }
                    else -> {
                        val match = ACCESS_CALLBACK.matchEntire(line)
                        val watched = match?.groupValues?.get(2)?.let(::watchingUid)
                        val caller = match?.groupValues?.get(3)?.let(UidStateParser::uidOf)
                        val pid = match?.groupValues?.get(4)?.toIntOrNull()
                        if (match == null || !match.groupValues[1].equals(accessKind.name, true) ||
                            !entryPending || operations == null || watched == null || caller == null || pid == null
                        ) {
                            malformed += accessKind
                        } else {
                            access += AccessWatcher(accessKind, checkNotNull(operations), watched, caller, pid)
                        }
                        entryPending = false
                        operations = null
                    }
                }
            } else if (section in MODE_SECTIONS) {
                when {
                    section == OP_MODES && line.startsWith("Op ") && line.endsWith(":") -> {
                        modeOp = line.removePrefix("Op ").removeSuffix(":").takeIf(OP::matches)
                        if (modeOp == null) malformedModes = true
                    }
                    section == PACKAGE_MODES && line.startsWith("Pkg ") && line.endsWith(":") -> {
                        modePackage = line.removePrefix("Pkg ").removeSuffix(":").takeIf { it.isNotBlank() }
                    }
                    else -> {
                        val match = MODE_CALLBACK.matchEntire(line)
                        val watched = match?.groupValues?.get(2)?.let(::watchingUid)
                        val caller = match?.groupValues?.get(4)?.let(UidStateParser::uidOf)
                        val pid = match?.groupValues?.get(5)?.toIntOrNull()
                        if (match == null || watched == null || caller == null || pid == null ||
                            (section == OP_MODES && modeOp == null) ||
                            (section == PACKAGE_MODES && modePackage == null)
                        ) {
                            malformedModes = true
                            continue
                        }
                        val id = match.groupValues[1]
                        val row = ModeWatcher(id, watched, caller, pid, match.groupValues[3])
                        val previous = modes[id]
                        if (previous != null && previous.copy(operations = emptySet(), packages = emptySet()) != row) {
                            malformedModes = true
                            continue
                        }
                        modes[id] = row.copy(
                            operations = previous?.operations.orEmpty() + listOfNotNull(modeOp),
                            packages = previous?.packages.orEmpty() + listOfNotNull(modePackage),
                        )
                    }
                }
            }
        }
        endEntry()
        return AppOpsWatcherSnapshot(
            recognized = output.lineSequence().firstOrNull()?.trim() == HEADER && recognizedSection &&
                !ERROR.containsMatchIn(output),
            accessWatchers = access,
            modeWatchers = modes.values.toList(),
            malformedAccessKinds = malformed,
            malformedModeWatchers = malformedModes,
        )
    }

    private fun watchingUid(value: String): Int? = if (value == "-1") -1 else UidStateParser.uidOf(value)

    private const val HEADER = "Current AppOps Service state:"
    private const val OP_MODES = "Op mode watchers:"
    private const val PACKAGE_MODES = "Package mode watchers:"
    private val MODE_SECTIONS = setOf(OP_MODES, PACKAGE_MODES, "All op mode watchers:")
    private val ACCESS_SECTIONS = mapOf(
        "All op active watchers:" to AccessWatchKind.ACTIVE,
        "All op started watchers:" to AccessWatchKind.STARTED,
        "All op noted watchers:" to AccessWatchKind.NOTED,
    )
    private val SECTIONS = MODE_SECTIONS + ACCESS_SECTIONS.keys
    private val OP = Regex("[A-Z_][A-Z0-9_]*")
    private val ENTRY = Regex("[0-9a-fA-F]+ ->")
    private val ACCESS_CALLBACK = Regex(
        """(Active|Started|Noted)Callback\{[0-9a-fA-F]+ watchinguid=(\S+) from uid=(\S+) pid=(\d+)\}""",
    )
    private val MODE_CALLBACK = Regex(
        """(?:#\d+|[0-9a-fA-F]+): ModeCallback\{([0-9a-fA-F]+) watchinguid=(\S+) flags=0x[0-9a-fA-F]+ op=(\S+) from uid=(\S+) pid=(\d+)\}""",
    )
    private val ERROR = Regex("(?i)permission denial|unknown option|exception|dump timed out|dump timeout")
}
