package dev.izumi.appopsnext.guard

import android.content.Context
import android.os.SystemClock
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.izumi.appopsnext.appops.AdaptiveScopeModeChangeExecutor
import dev.izumi.appopsnext.appops.AppOpsRepository
import dev.izumi.appopsnext.appops.RevokedCompatRetry
import dev.izumi.appopsnext.appops.RevokedCompatWriteMemory
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpIdentifier
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.appops.model.AppOpNames
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.diagnostics.DiagnosticLogRepository
import dev.izumi.appopsnext.monitor.ForegroundStateProbe
import dev.izumi.appopsnext.shizuku.PrivilegedServiceClient
import dev.izumi.appopsnext.shizuku.model.PrivilegedServiceState
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Runs guard checks and keeps the chosen trigger running.
 *
 * Reads go through [AppOpsRepository.readMode], which uses the binder when the
 * read probe allowed it and needs only Shizuku then. Restores write through the
 * same pipeline as automatic new-app rules and need the privileged backend, which
 * a background check connects on demand.
 */
class GuardController(
    private val context: Context,
    private val repository: GuardRepository,
    private val appOpsRepository: AppOpsRepository,
    private val privilegedServiceClient: PrivilegedServiceClient,
    private val diagnosticLog: DiagnosticLogRepository,
    private val scope: CoroutineScope,
    writeMemory: RevokedCompatWriteMemory,
    private val notifier: GuardNotifier = GuardNotifier(context),
) {
    private val checkMutex = Mutex()
    private val userWrites = GuardUserWrites(checkMutex)
    private val adaptiveScopeExecutor = AdaptiveScopeModeChangeExecutor(writeMemory) { uid ->
        context.packageManager.getPackagesForUid(uid)?.toList().orEmpty()
    }
    private val foregroundStates = ForegroundStateProbe(privilegedServiceClient)
    private val checker = GuardChecker(readMode = ::readMode, restore = ::restore, isOnScreen = ::isOnScreen)
    private val mutableRestores = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits after a check wrote a guarded mode back, so open pages can reload what they show. */
    val restores: SharedFlow<Unit> = mutableRestores.asSharedFlow()

    /** Counted for the cost comparison between the two modes; reset with the process. */
    private val checkCount = AtomicLong()
    private val checkMillis = AtomicLong()
    private val callbackCount = AtomicLong()

    fun start() {
        scope.launch {
            combine(repository.mode, repository.rules) { mode, rules -> mode to rules.isNotEmpty() }
                .distinctUntilChanged()
                .collect { (mode, hasRules) -> applyMode(if (hasRules) mode else GuardMode.OFF) }
        }
        scope.launch {
            privilegedServiceClient.state
                .filterIsInstance<PrivilegedServiceState.Connected>()
                .collect { runCheckIfEnabled("backend_connected") }
        }
    }

    fun onAppForeground() {
        scope.launch {
            // Retries a realtime watch that could not start while the process was in the background.
            if (repository.mode.first() == GuardMode.REALTIME && repository.rules.first().isNotEmpty()) {
                runCatching { GuardWatchService.start(context) }
            }
            runCheckIfEnabled("app_foreground")
        }
    }

    fun onModeCallback() {
        callbackCount.incrementAndGet()
    }

    suspend fun runCheckIfEnabled(trigger: String, operations: Set<String>? = null) {
        if (repository.mode.first() == GuardMode.OFF) return
        runCheck(trigger, operations)
    }

    /**
     * A check for [GuardWorker]. Its check may replace the worker's own one-time
     * work, which cancels the worker, so the check runs in the controller's scope.
     */
    suspend fun runWorkerCheck(trigger: String) {
        scope.async { runCheckIfEnabled(trigger) }.await()
    }

    /** [operations] limits the check to rules of these `android:` names, as a mode callback does. */
    suspend fun runCheck(trigger: String, operations: Set<String>? = null): GuardChecker.Result? =
        checkMutex.withLock {
            val allRules = repository.rules.first()
            val rules = allRules.filter {
                (operations == null || it.stableOperationName in operations) && !userWrites.shouldSkip(it)
            }
            if (rules.isEmpty()) return@withLock null
            val restoreLimit = repository.restoreLimit.first()
            val started = SystemClock.elapsedRealtime()
            val result = try {
                checker.check(rules, repository.states(), restoreLimit)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                diagnosticLog.error(LOG_SOURCE, "Guard check failed. trigger=$trigger", error)
                return@withLock null
            }
            repository.saveCheck(result.states, result.events)
            scheduleDelayedRestore(
                GuardRestorePolicy.nextDue(allRules, result.states, System.currentTimeMillis(), restoreLimit),
            )
            notifier.notifyEvents(result.events)
            if (result.events.any { it.kind == GuardEventKind.RESTORED }) mutableRestores.tryEmit(Unit)
            val millis = SystemClock.elapsedRealtime() - started
            diagnosticLog.info(
                LOG_SOURCE,
                "Guard check. trigger=$trigger, rules=${rules.size}, events=${result.events.size}, " +
                    "readFailures=${result.readFailures}, millis=$millis, " +
                    "processChecks=${checkCount.incrementAndGet()}, " +
                    "processMillis=${checkMillis.addAndGet(millis)}, " +
                    "processCallbacks=${callbackCount.get()}",
            )
            result
        }

    internal suspend fun <T> withUserWrites(
        targets: Collection<GuardWriteTarget>,
        action: suspend () -> T,
    ): T = userWrites.run(targets, recheck = { operations ->
        scope.launch { runCheckIfEnabled("user_write_finished", operations) }
    }, action)

    /**
     * Records a mode the user set in the app, so the guard does not take it for a drift.
     *
     * With [guard] the rules of this setting become one rule for [mode] in [scope],
     * keeping their automatic restore and its delay. Without it they are removed.
     */
    internal suspend fun onUserChange(
        packageName: String,
        uid: Int,
        operationName: String,
        scope: AppOpScope,
        mode: AppOpMode,
        guard: Boolean,
    ) = withContext(NonCancellable) {
        val stableName = AppOpNames.stableName(operationName)
        repository.replaceSetting(packageName, stableName) { existing ->
            if (!guard) {
                null
            } else {
                GuardRule(
                    packageName = packageName,
                    uid = uid,
                    stableOperationName = stableName,
                    scope = scope,
                    expectedMode = mode,
                    autoRestore = false,
                ).keepingRestoreOf(existing)
            }
        }
    }

    /** The in-process timer of the next delayed restore; guarded by [checkMutex]. */
    private var delayedRestoreJob: Job? = null

    /**
     * Checks again when a waiting restore falls due. The timer covers a running
     * process, which the realtime service keeps. The one-time work covers a process
     * that was stopped meanwhile and may run later than due. Each check replaces both.
     */
    private fun scheduleDelayedRestore(dueMillis: Long?) {
        delayedRestoreJob?.cancel()
        delayedRestoreJob = null
        val workManager = WorkManager.getInstance(context)
        if (dueMillis == null) {
            workManager.cancelUniqueWork(DELAYED_WORK_NAME)
            return
        }
        val waitMillis = (dueMillis - System.currentTimeMillis()).coerceAtLeast(0)
        delayedRestoreJob = scope.launch {
            delay(waitMillis)
            // The check reschedules, which cancels this timer, so it runs apart from it.
            scope.launch { runCheckIfEnabled(TRIGGER_DELAYED) }
        }
        workManager.enqueueUniqueWork(
            DELAYED_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<GuardWorker>()
                .setInitialDelay(waitMillis + DELAYED_WORK_SLACK_MILLIS, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(GuardWorker.KEY_TRIGGER to TRIGGER_DELAYED))
                .build(),
        )
    }

    /** Touched only by the collector in [start], one value at a time. */
    private var appliedMode: GuardMode? = null

    private fun applyMode(mode: GuardMode) {
        val workManager = WorkManager.getInstance(context)
        if (mode == GuardMode.PERIODIC) {
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<GuardWorker>(1, TimeUnit.HOURS).build(),
            )
        } else {
            workManager.cancelUniqueWork(WORK_NAME)
        }
        if (mode == GuardMode.REALTIME) {
            // A process started in the background may not start a foreground service;
            // the next app start, which runs this again, brings the watch back.
            runCatching { GuardWatchService.start(context) }.onFailure { error ->
                diagnosticLog.warning(
                    LOG_SOURCE,
                    "Realtime guard not started from the background. ${error::class.java.simpleName}",
                )
            }
        } else {
            GuardWatchService.stop(context)
        }
        if (mode == GuardMode.OFF) workManager.cancelUniqueWork(DELAYED_WORK_NAME)
        // Applied at every process start, where off, the default, says nothing.
        // Turning a running guard off is still written.
        val previous = appliedMode
        appliedMode = mode
        if (mode != GuardMode.OFF || (previous != null && previous != GuardMode.OFF)) {
            diagnosticLog.info(LOG_SOURCE, "Guard mode applied. mode=${mode.name}")
        }
    }

    private suspend fun readMode(rule: GuardRule): AppOpMode? =
        appOpsRepository.readMode(rule.packageName, rule.identifier, rule.scope)
            ?: if (privilegedServiceClient.ensureConnected()) appOpsRepository.readMode(rule.packageName, rule.identifier, rule.scope) else null

    /** Not on screen when the backend cannot be reached or the state cannot be read, so the restore goes ahead. */
    private suspend fun isOnScreen(rule: GuardRule): Boolean {
        if (!privilegedServiceClient.ensureConnected()) return false
        return foregroundStates.isOnScreen(rule.uid, rule.packageName)
    }

    private suspend fun restore(rule: GuardRule): Boolean {
        if (!privilegedServiceClient.ensureConnected()) return false
        val operation = rule.identifier
        val outcome = appOpsRepository.withWriteTransaction { transaction ->
            adaptiveScopeExecutor.execute(
                packageName = rule.packageName,
                uid = rule.uid,
                preferredScope = rule.scope,
                requestedMode = rule.expectedMode,
                readMode = { scope -> appOpsRepository.readMode(rule.packageName, operation, scope) },
                revokedCompatRetry = RevokedCompatRetry(operationName = rule.stableOperationName) { permission ->
                    transaction.applyRevokedCompat(
                        packageName = rule.packageName,
                        uid = rule.uid,
                        operation = operation,
                        permissionName = permission,
                        requestedMode = rule.expectedMode,
                    )
                },
            ) { scope ->
                transaction.applyMode(
                    packageName = rule.packageName,
                    operation = operation,
                    scope = scope,
                    requestedMode = rule.expectedMode,
                )
            }
        }
        diagnosticLog.info(
            LOG_SOURCE,
            "Guard restore. package=${rule.packageName}, operation=${rule.stableOperationName}, " +
                "result=${outcome.result::class.java.simpleName}",
        )
        return outcome.result is AppOpModeChangeResult.Success
    }

    private val GuardRule.identifier: AppOpIdentifier
        get() = AppOpIdentifier(
            stableName = stableOperationName,
            shellName = AppOpNames.shellName(stableOperationName),
        )

    private companion object {
        const val LOG_SOURCE = "Guard"
        const val WORK_NAME = "guard_periodic_check"
        const val DELAYED_WORK_NAME = "guard_delayed_restore"
        const val TRIGGER_DELAYED = "delayed_restore"

        /** Lets the in-process timer go first, so a running process does not check twice. */
        const val DELAYED_WORK_SLACK_MILLIS = 30_000L
    }
}
