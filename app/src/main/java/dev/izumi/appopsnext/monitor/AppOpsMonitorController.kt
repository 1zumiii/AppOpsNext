package dev.izumi.appopsnext.monitor

import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import dev.izumi.appopsnext.BuildConfig
import dev.izumi.appopsnext.apps.InstalledAppsRepository
import dev.izumi.appopsnext.diagnostics.DiagnosticLogRepository
import dev.izumi.appopsnext.settings.UserSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference

/**
 * Owns the AppOps watch registration and turns raw callbacks into notifications.
 *
 * Events arrive on a binder thread, so everything here is either atomic or
 * handed to [scope].
 */
class AppOpsMonitorController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val installedAppsRepository: InstalledAppsRepository,
    private val targetsRepository: MonitorTargetsRepository,
    private val settingsRepository: UserSettingsRepository,
    private val diagnosticLog: DiagnosticLogRepository,
    private val notifier: MonitorNotifier = MonitorNotifier(context),
) {
    private val client = AppOpsMonitorClient()
    private val mutableRecentAccesses = MutableStateFlow<List<MonitoredAccess>>(emptyList())
    private val mutableStatus = MutableStateFlow<MonitorStatus?>(null)
    private val selfCheckProbe = AtomicReference<((AppOpAccessEvent) -> Unit)?>(null)
    private val watched = AtomicReference<Map<String, Set<String>>>(emptyMap())
    private val accessLock = Any()
    private val ownPackage = context.packageName
    private var watchdog: Job? = null

    val recentAccesses: StateFlow<List<MonitoredAccess>> =
        mutableRecentAccesses.asStateFlow()

    val status: StateFlow<MonitorStatus?> = mutableStatus.asStateFlow()

    val isRunning: Boolean
        get() = client.isRegistered

    /**
     * Registers watches for the operations the user picked.
     *
     * @param extraOperations operations to watch on top of the user's selection,
     * used by the self-check for an operation it can trigger on purpose.
     */
    suspend fun start(extraOperations: Set<String> = emptySet()): Result<MonitorStatus> {
        val targets = targetsRepository.targets.first()
        watched.set(targets.associate { it.packageName to it.operationNames })
        val operationNames = targets.flatMapTo(mutableSetOf()) { it.operationNames } +
            extraOperations
        val opCodes = operationNames.mapNotNull(AppOpCodes::codeOf).toIntArray()
        if (opCodes.isEmpty()) {
            return Result.failure(IllegalStateException("No operations are selected"))
        }
        return runCatching {
            val registration = client.register(opCodes) { event -> onAccess(event) }
            val status = MonitorStatus(
                activeWatch = registration.active,
                notedWatch = registration.noted,
                startedWatch = registration.started,
                transactionCodesFromPlatform = registration.transactionCodesFromPlatform,
                watchedPackages = targets.size,
                watchedOperations = operationNames.size,
            )
            mutableStatus.value = status
            diagnosticLog.info(
                source = LOG_SOURCE,
                message = "Monitor registered. packages=${targets.size}, " +
                    "ops=${opCodes.size}, active=${registration.active}, " +
                    "noted=${registration.noted}, started=${registration.started}, " +
                    "codesFromPlatform=${registration.transactionCodesFromPlatform}" +
                    registration.failures.joinToString(
                        prefix = if (registration.failures.isEmpty()) "" else ", failures=[",
                        postfix = if (registration.failures.isEmpty()) "" else "]",
                    ),
            )
            check(registration.any) {
                "No watch could be registered: ${registration.failures.joinToString()}"
            }
            startWatchdog()
            status
        }.onFailure { error ->
            diagnosticLog.error(
                source = LOG_SOURCE,
                message = "Unable to register the AppOps monitor.",
                error = error,
            )
            runCatching { client.unregister() }
            mutableStatus.value = null
        }
    }

    /**
     * Rebuilds the notifications, for example after a language change.
     *
     * This owns the ongoing notification's content as well, so a caller does not
     * have to know whether any accesses have been folded into it yet.
     */
    fun refreshNotifications() {
        val accesses = mutableRecentAccesses.value
        scope.launch {
            if (accesses.isEmpty()) {
                notifier.postOngoing()
                return@launch
            }
            val headsUp = runCatching {
                settingsRepository.settings.first().monitorHeadsUp
            }.getOrDefault(false)
            notifier.notifyAccesses(accesses, headsUp)
        }
    }

    /**
     * Drops the reported accesses and returns the notification to its plain
     * form, so a count that has been read and dismissed does not carry on from
     * where it left off.
     */
    fun clearAccesses() {
        synchronized(accessLock) { mutableRecentAccesses.value = emptyList() }
        notifier.postOngoing()
    }

    fun stop() {
        watchdog?.cancel()
        watchdog = null
        runCatching { client.unregister() }
        mutableStatus.value = null
        mutableRecentAccesses.value = emptyList()
        diagnosticLog.info(source = LOG_SOURCE, message = "Monitor stopped.")
    }

    /**
     * Nothing tells us when a registration is dropped, which happens if the
     * system service restarts. Re-registering on a slow timer costs one binder
     * ping per interval and keeps the monitor from being silently dead.
     */
    private fun startWatchdog() {
        watchdog?.cancel()
        watchdog = scope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MILLIS)
                if (!client.isRegistered) break
                if (!client.isServiceAlive()) {
                    diagnosticLog.warning(
                        source = LOG_SOURCE,
                        message = "AppOps service went away; re-registering the monitor.",
                    )
                    runCatching { client.unregister() }
                    start()
                    break
                }
            }
        }
    }

    /**
     * Registers the watches, reads this app's own clipboard, and reports whether
     * the access came back.
     *
     * The transaction is forwarded by Shizuku, so the system sees the shell UID
     * as the caller. Without WATCH_APPOPS the watch is narrowed to that UID
     * rather than refused, and this app's own read would never be delivered.
     * An event arriving therefore shows the watch really does span other UIDs,
     * which a registration that merely returned without throwing would not.
     */
    suspend fun runSelfCheck(): MonitorSelfCheckResult {
        val registration = start(extraOperations = setOf(AppOpCodes.SELF_CHECK_OP))
        registration.exceptionOrNull()?.let { error ->
            return MonitorSelfCheckResult.Failed(
                error.message ?: error::class.java.simpleName,
            )
        }

        val clipboardOp = AppOpCodes.codeOf(AppOpCodes.SELF_CHECK_OP)
            ?: return MonitorSelfCheckResult.Failed("Unknown self-check operation")

        // Any operation reported for this package during the window is accepted,
        // and the code that actually arrived is recorded. Requiring the expected
        // code would report a vendor image that renumbered its operations as an
        // unsupported device, which is the wrong diagnosis.
        val observedCode = MutableStateFlow<Int?>(null)
        selfCheckProbe.set { event ->
            if (event.packageName == ownPackage) {
                observedCode.compareAndSet(null, event.opCode)
            }
        }
        try {
            // Reading requires window focus, so this only runs from the settings
            // screen while the app is in the foreground.
            withContext(Dispatchers.Main) {
                runCatching {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.primaryClip
                }
            }
            val reported = withTimeoutOrNull(SELF_CHECK_TIMEOUT_MILLIS) {
                while (observedCode.value == null) delay(SELF_CHECK_POLL_MILLIS)
                observedCode.value
            }
            diagnosticLog.info(
                source = LOG_SOURCE,
                message = "Self-check finished. expectedOp=$clipboardOp, " +
                    "reportedOp=${reported ?: "none"}",
            )
            if (reported == null) return MonitorSelfCheckResult.NoEvent
            if (reported != clipboardOp) {
                // The callbacks work, so the watch itself is fine; the operation
                // numbering on this image does not match the platform sources
                // this table was built from.
                diagnosticLog.warning(
                    source = LOG_SOURCE,
                    message = "Operation codes differ from the expected table. " +
                        "${AppOpCodes.SELF_CHECK_OP} reported as $reported, " +
                        "expected $clipboardOp. Monitoring was not enabled.",
                )
                return MonitorSelfCheckResult.Failed(
                    "operation code mismatch ($reported != $clipboardOp)",
                )
            }
            return MonitorSelfCheckResult.Passed
        } finally {
            selfCheckProbe.set(null)
        }
    }

    private fun onAccess(event: AppOpAccessEvent) {
        // Raw events only, and only in debug builds: the platform reports one
        // logical access more than once, which is not visible anywhere else.
        if (BuildConfig.DEBUG) {
            Log.d(
                RAW_LOG_TAG,
                "kind=${event.kind} op=${event.opCode}/${AppOpCodes.nameOf(event.opCode)} " +
                    "uid=${event.uid} pkg=${event.packageName} " +
                    "allowed=${event.allowed} t=${System.currentTimeMillis()}",
            )
        }
        selfCheckProbe.get()?.invoke(event)
        if (event.packageName == ownPackage) return
        val operationName = AppOpCodes.nameOf(event.opCode) ?: return
        // Watches are registered per operation for every package, so the
        // selection is applied here.
        if (operationName !in watched.get()[event.packageName].orEmpty()) return

        scope.launch {
            val app = runCatching {
                installedAppsRepository.loadInstalledApps()
                    .firstOrNull { it.packageName == event.packageName }
            }.getOrNull()

            val access = MonitoredAccess(
                operationName = operationName,
                packageName = event.packageName,
                appLabel = app?.label ?: event.packageName,
                kind = event.kind,
                allowed = event.allowed,
                observedAtMillis = System.currentTimeMillis(),
            )
            // Repeats inside the window are counted into the existing entry
            // rather than dropped. Dropping them lost the fact that the access
            // happened several times, and left the count on the icon wrong.
            val (updated, isNew) = synchronized(accessLock) {
                val current = mutableRecentAccesses.value
                val index = current.indexOfFirst { it.repeats(access) }
                val existing = current.getOrNull(index)
                val result = when {
                    existing == null ->
                        (listOf(access) + current).take(MAX_RECENT) to true

                    // One read can be noted several times a few milliseconds
                    // apart, so anything this close is the same access rather
                    // than the app touching the operation again.
                    access.observedAtMillis - existing.observedAtMillis <
                        SAME_ACCESS_WINDOW_MILLIS -> null to false

                    else -> {
                        val merged = existing.copy(
                            count = existing.count + 1,
                            observedAtMillis = access.observedAtMillis,
                        )
                        buildList {
                            add(merged)
                            current.forEachIndexed { at, item -> if (at != index) add(item) }
                        } to false
                    }
                }
                result.first?.let { mutableRecentAccesses.value = it }
                result
            }
            if (updated == null) return@launch
            val headsUp = runCatching {
                settingsRepository.settings.first().monitorHeadsUp
            }.getOrDefault(false)
            // A repeat updates the notification without interrupting again; only
            // a genuinely new access is worth a second heads-up.
            notifier.notifyAccesses(updated, headsUp = headsUp && isNew)
        }
    }

    /** Same app, operation and outcome, close enough in time to be one entry. */
    private fun MonitoredAccess.repeats(other: MonitoredAccess): Boolean =
        packageName == other.packageName &&
            operationName == other.operationName &&
            allowed == other.allowed &&
            other.observedAtMillis - observedAtMillis < COALESCE_WINDOW_MILLIS

    private companion object {
        const val LOG_SOURCE = "Monitor"
        const val SELF_CHECK_TIMEOUT_MILLIS = 4_000L
        const val SELF_CHECK_POLL_MILLIS = 50L
        const val SAME_ACCESS_WINDOW_MILLIS = 1_000L
        const val COALESCE_WINDOW_MILLIS = 60_000L
        const val RAW_LOG_TAG = "AppOpsMonitorRaw"
        const val WATCHDOG_INTERVAL_MILLIS = 5 * 60_000L
        const val MAX_RECENT = 100
    }
}
