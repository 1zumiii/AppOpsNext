package dev.izumi.appopsnext.guard

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpScope

/** How guarded settings are checked. Both active modes are kept until their cost is measured. */
enum class GuardMode {
    OFF,

    /** WorkManager every hour, plus app start and backend connection. */
    PERIODIC,

    /** A foreground service watching mode changes of the guarded operations. */
    REALTIME,
}

/** One setting the user asked to keep. */
data class GuardRule(
    val packageName: String,
    val uid: Int,
    val stableOperationName: String,
    val scope: AppOpScope,
    val expectedMode: AppOpMode,
    val autoRestore: Boolean,
) {
    val id: String get() = ruleId(packageName, stableOperationName, scope)

    companion object {
        fun ruleId(packageName: String, stableOperationName: String, scope: AppOpScope): String =
            "$packageName|$stableOperationName|${scope.name}"
    }
}

enum class GuardEventKind {
    /** Changed away from the expected mode and left as found. */
    DRIFT,
    RESTORED,
    RESTORE_FAILED,

    /** Changed again after three restores in a day; restoring stops until the window passes. */
    RESTORE_LIMIT,
}

data class GuardEvent(
    val timeMillis: Long,
    val packageName: String,
    val stableOperationName: String,
    val scope: AppOpScope,
    val expectedMode: AppOpMode,
    val observedMode: AppOpMode,
    val kind: GuardEventKind,
)

/** What one rule remembers between checks. */
data class GuardRuleState(
    /** The mode the last check saw, so an unchanged drift is reported once. */
    val lastObserved: AppOpMode? = null,
    /** Restore attempts, successful or not, within the policy window. */
    val restoreTimes: List<Long> = emptyList(),
)
