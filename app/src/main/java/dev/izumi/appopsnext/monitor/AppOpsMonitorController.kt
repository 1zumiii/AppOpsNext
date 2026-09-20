package dev.izumi.appopsnext.monitor

import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
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
    private val lastNotifiedAt = HashMap<String, Long>()
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

    /** Re-posts the access notification, for example after a language change. */
    fun refreshNotifications() {
        val accesses = mutableRecentAccesses.value
        if (accesses.isEmpty()) return
        scope.launch {
            val headsUp = runCatching {
                settingsRepository.settings.first().monitorHeadsUp
            }.getOrDefault(false)
            notifier.notifyAccesses(accesses, headsUp)
        }
    }

    fun stop() {
        watchdog?.cancel()
        watchdog = null
        runCatching { client.unregister() }
        synchronized(lastNotifiedAt) { lastNotifiedAt.clear() }
        mutableStatus.value = null
        mutableRecentAccesses.value = emptyList()
        notifier.clearAccesses()
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
        selfCheckProbe.get()?.invoke(event)
        if (event.packageName == ownPackage) return
        val operationName = AppOpCodes.nameOf(event.opCode) ?: return
        // Watches are registered per operation for every package, so the
        // selection is applied here.
        if (operationName !in watched.get()[event.packageName].orEmpty()) return

        // The same access can arrive repeatedly while it stays open; one entry
        // per app, operation and outcome within the window is enough.
        val key = "${event.packageName}|$operationName|${event.allowed}"
        val now = SystemClock.elapsedRealtime()
        synchronized(lastNotifiedAt) {
            val previous = lastNotifiedAt[key]
            if (previous != null && now - previous < COALESCE_WINDOW_MILLIS) return
            lastNotifiedAt[key] = now
        }

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
            val updated = (listOf(access) + mutableRecentAccesses.value).take(MAX_RECENT)
            mutableRecentAccesses.value = updated
            // One notification carrying every access keeps the status bar to a
            // single icon however many arrive.
            val headsUp = runCatching {
                settingsRepository.settings.first().monitorHeadsUp
            }.getOrDefault(false)
            notifier.notifyAccesses(updated, headsUp)
        }
    }

    private companion object {
        const val LOG_SOURCE = "Monitor"
        const val SELF_CHECK_TIMEOUT_MILLIS = 4_000L
        const val SELF_CHECK_POLL_MILLIS = 50L
        const val COALESCE_WINDOW_MILLIS = 60_000L
        const val WATCHDOG_INTERVAL_MILLIS = 5 * 60_000L
        const val MAX_RECENT = 100
    }
}
