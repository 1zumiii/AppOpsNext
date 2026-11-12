package dev.izumi.appopsnext.monitor

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.os.UserHandle
import dev.izumi.appopsnext.apps.InstalledAppsRepository
import dev.izumi.appopsnext.appops.PrivilegedAppOpsGateway
import dev.izumi.appopsnext.appops.AppOpsWatchersRepository
import dev.izumi.appopsnext.appops.parser.AccessWatchKind
import dev.izumi.appopsnext.appops.parser.WatchRegistration
import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.diagnostics.DiagnosticLogRepository
import dev.izumi.appopsnext.settings.UserSettingsRepository
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/** One cancellable session owns registration, callbacks and notification publication. */
class AppOpsMonitorController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val installedAppsRepository: InstalledAppsRepository,
    private val gateway: PrivilegedAppOpsGateway,
    private val targetsRepository: MonitorTargetsRepository,
    private val settingsRepository: UserSettingsRepository,
    private val diagnosticLog: DiagnosticLogRepository,
    private val eventLog: MonitorEventLog,
    private val notifier: MonitorNotifier = MonitorNotifier(context),
    /**
     * Finds out whether the device has MIUI private operations, reading the
     * given packages first. Only a selection that names one asks.
     */
    private val probeVendorOps: suspend (Collection<String>) -> Set<Int> = { emptySet() },
) {
    private val foregroundStates = ForegroundStateProbe(gateway)
    private val client = AppOpsMonitorClient()
    private val watchers = AppOpsWatchersRepository(gateway)
    private val lifecycle = MonitorLifecycle<MonitorStatus>(scope) { client.unregister() }
    private val mutableStatus = MutableStateFlow<MonitorStatus?>(null)
    private val mutableFailure = MutableStateFlow<String?>(null)
    private val ownUid = context.applicationInfo.uid
    // Accessed only inside lifecycle.publish/clear/stop.
    private var sessionData: SessionData? = null

    val status = mutableStatus.asStateFlow()
    val failure = mutableFailure.asStateFlow()
    val isRunning: Boolean get() = mutableStatus.value != null

    /** Points watched by the running session, or null before one has started. */
    val watchedPointCount: Int?
        get() = lifecycle.session()?.let { session ->
            lifecycle.publish(session) { sessionData?.pointCount }
        }

    private class SessionData(
        /** The selected operations as the watcher registry prints them. */
        val operationNames: Set<String>,
        /** The monitor's own notification setting, which a point may override. */
        val defaultHeadsUp: Boolean,
        /** Whether any point may interrupt, which fixes the channel for the session. */
        val alertChannel: Boolean,
        val apps: Map<String, InstalledApp>,
        val watched: Map<String, Set<String>>,
        /** Per-point settings, for the points that are actually watched. */
        val points: Map<Pair<String, String>, MonitorPointSettings>,
        val accumulator: MonitorAccessAccumulator = MonitorAccessAccumulator(),
    ) {
        /** The private registration calls of the current registration. */
        var vendorOutcomes: List<VendorWatchOutcome> = emptyList()

        /** Private codes whose first event has been written to the diagnostic log. */
        val loggedVendorCodes: MutableSet<Int> = mutableSetOf()

        /**
         * Whether the registry has matched this session's registration. Until
         * it has, a miss may only mean this device prints the registry
         * differently, so it is not taken as a lost registration.
         */
        var registryConfirmed = false

        val pointCount: Int get() = watched.values.sumOf { it.size }
    }

    private data class QueuedAccess(
        val event: AppOpAccessEvent,
        val wallTime: Long,
        val elapsedTime: Long,
        val revision: Long,
    )

    suspend fun start(): Result<MonitorStatus> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(lifecycle.start { session ->
                    val targets = targetsRepository.targets.first()
                    val names = targets.flatMapTo(mutableSetOf()) { it.operationNames }
                    // A selection of standard operations alone never reads anything
                    // extra, so such a session starts as it always has.
                    if (names.any(MonitorOpCodes::isVendor)) {
                        // A failed read leaves the private operations without a
                        // code, which the check below reports like any unknown one.
                        val offered = try {
                            probeVendorOps(targets.map(MonitorTarget::packageName))
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            diagnosticLog.warning(LOG_SOURCE, "MIUI operation probe failed: ${error.message}")
                            emptySet()
                        }
                        diagnosticLog.info(LOG_SOURCE, "MIUI operations offered on this device: $offered")
                    }
                    val split = MonitorOpCodes.split(names)
                    val codes = split.standard
                    val vendorCodes = split.vendor
                    check(codes.isNotEmpty() || vendorCodes.isNotEmpty()) { "No operations are selected" }
                    check(split.unknown.isEmpty()) { "Some selected operations have no known watch code" }
                    val watchedOperations = targets.associate { it.packageName to it.operationNames }
                    val defaultHeadsUp = settingsRepository.settings.first().monitorHeadsUp
                    // Settings for a point that is no longer watched are kept in
                    // storage but have nothing to act on, so only the watched ones
                    // are carried into the session.
                    val points = targetsRepository.pointSettings.first()
                        .filter { it.operationName in watchedOperations[it.packageName].orEmpty() }
                        .associateBy { it.packageName to it.operationName }
                    val data = SessionData(
                        operationNames = codes.asList().mapNotNullTo(mutableSetOf(), AppOpCodes::registryNameOf),
                        defaultHeadsUp = defaultHeadsUp,
                        // A channel's importance is fixed once it is created, so the
                        // session has to settle on one up front: the loud one as
                        // soon as anything is allowed to interrupt.
                        alertChannel = defaultHeadsUp || points.values.any { it.headsUp == true },
                        apps = installedAppsRepository.loadInstalledApps().associateBy { it.packageName },
                        watched = watchedOperations,
                        points = points,
                    )
                    val events = Channel<QueuedAccess>(EVENT_QUEUE_CAPACITY)
                    val pendingPost = Channel<Unit>(Channel.CONFLATED)
                    val dropped = AtomicLong()
                    // Read and written only inside lifecycle.publish, which is the lock.
                    var alertPending = false
                    val currentUser = Process.myUserHandle()
                    session.job.invokeOnCompletion { events.cancel(); pendingPost.cancel() }
                    val onAccess: (AppOpAccessEvent) -> Unit = { event ->
                        val wallTime = System.currentTimeMillis()
                        val elapsedTime = SystemClock.elapsedRealtime()
                        lifecycle.publish(session) {
                            val name = MonitorOpCodes.nameOf(event.opCode)
                            // The user handle keeps other profiles, such as a private
                            // space, out even when they hold the same package name. An
                            // app that is missing from the snapshot was installed after
                            // this session started; reporting it under its package name
                            // beats dropping the access.
                            val known = data.apps[event.packageName]
                            if (event.uid != ownUid &&
                                UserHandle.getUserHandleForUid(event.uid) == currentUser &&
                                (known == null || known.uid == event.uid) &&
                                name in data.watched[event.packageName].orEmpty()
                            ) {
                                val queued =
                                    QueuedAccess(event, wallTime, elapsedTime, session.revision)
                                // Losing an event costs one line of a list. Stopping the
                                // monitor because one arrived too fast costs every event
                                // after it, so a full queue is reported, not fatal.
                                if (events.trySend(queued).isFailure &&
                                    dropped.getAndIncrement() == 0L
                                ) {
                                    diagnosticLog.warning(
                                        LOG_SOURCE,
                                        "Monitor event queue is full; events are being dropped.",
                                    )
                                }
                            }
                        }
                    }
                    val registrationRevision = AtomicLong()
                    fun register(): MonitorStatus {
                        val revision = registrationRevision.incrementAndGet()
                        val malformed = AtomicReference<String?>(null)
                        val registration = client.register(
                            codes,
                            onAccess,
                            onMalformed = { reason ->
                                lifecycle.publish(session) {
                                    if (registrationRevision.get() == revision) {
                                        malformed.set(reason)
                                        mutableStatus.value = mutableStatus.value?.copy(callbackFailure = reason)
                                        diagnosticLog.warning(LOG_SOURCE, "Rejected AppOps callback: $reason")
                                    }
                                }
                            },
                            vendorOpCodes = vendorCodes,
                        )
                        logVendorRegistration(registration)
                        val result = MonitorSessionStatus.of(
                            hasStandard = codes.isNotEmpty(),
                            registration = registration,
                            watchedPackages = targets.size,
                            watchedOperations = names.size,
                        )
                        diagnosticLog.info(LOG_SOURCE, "Monitor registered. status=$result, " +
                            "failures=${registration.failures.joinToString()}")
                        lifecycle.publish(session) {
                            data.vendorOutcomes = registration.vendor
                            sessionData = data
                            mutableFailure.value = null
                            mutableStatus.value = result.copy(callbackFailure = malformed.get())
                        }
                        return result
                    }
                    val result = register()
                    session.scope.launch {
                        // The filters run cheapest first: asking the platform where
                        // an application was running costs a command, so it is only
                        // asked about an access that would otherwise be reported.
                        for (queued in events) {
                            val event = queued.event
                            val name = MonitorOpCodes.nameOf(event.opCode) ?: continue
                            // Written down before any setting decides whether it interrupts.
                            eventLog.record(
                                MonitorLogEntry(queued.wallTime, event.uid, event.packageName, name, event.allowed),
                                queued.elapsedTime,
                            )
                            if (MonitorOpCodes.isVendor(name)) logFirstVendorEvent(session, data, event)
                            val settings = data.points[event.packageName to name]
                            // Someone watching a permission they have just denied
                            // wants the refusals, not everything.
                            val outcomes = settings?.outcomes ?: MonitorOutcomes.ALL
                            if (!outcomes.reports(event.allowed)) continue
                            val access = MonitoredAccess(
                                uid = event.uid,
                                operationName = name,
                                packageName = event.packageName,
                                appLabel = data.apps[event.packageName]?.label ?: event.packageName,
                                kind = event.kind,
                                allowed = event.allowed,
                                observedAtMillis = queued.wallTime,
                                elapsedRealtimeMillis = queued.elapsedTime,
                            )
                            val worthReporting = lifecycle.publish(session, queued.revision) {
                                data.accumulator.wouldReport(access, settings?.throttleMillis)
                            } ?: break
                            if (!worthReporting) continue
                            if (settings?.backgroundOnly == true &&
                                foregroundStates.isOnScreen(event.uid, event.packageName)
                            ) {
                                continue
                            }
                            lifecycle.publish(session, queued.revision) {
                                if (data.accumulator.add(access, settings?.throttleMillis)) {
                                    if (settings?.headsUp(data.defaultHeadsUp)
                                        ?: data.defaultHeadsUp
                                    ) {
                                        alertPending = true
                                    }
                                    pendingPost.trySend(Unit)
                                }
                            } ?: break
                        }
                    }
                    session.scope.launch {
                        // One notification per burst rather than one per event. The
                        // platform sheds updates from a package that posts too quickly,
                        // and an operation such as location can be reported several
                        // times a second. The signal is conflated, so the last event of
                        // a burst is still published.
                        for (signal in pendingPost) {
                            lifecycle.publish(session) {
                                val alert = alertPending
                                alertPending = false
                                postNotification(data, alert)
                            }
                            delay(NOTIFY_INTERVAL_MILLIS)
                        }
                    }
                    session.scope.launch {
                        while (isActive) {
                            delay(WATCHDOG_INTERVAL_MILLIS)
                            val registry = checkRegistration()
                            val confirmed = lifecycle.publish(session) { data.registryConfirmed } ?: break
                            // A session the user enabled without a registry match
                            // falls back to asking whether the service is alive.
                            val lost = if (confirmed) {
                                registry == WatchRegistration.MISSING
                            } else {
                                !client.isServiceAlive()
                            }
                            if (!lost) continue
                            val recovery = retryMonitorRegistration {
                                lifecycle.reconnect(session, ::register)
                                val reregistered = checkRegistration()
                                check(!confirmed || reregistered != WatchRegistration.MISSING) {
                                    "AppOps watch registration is missing from the system registry"
                                }
                            }
                            if (recovery.isFailure) {
                                val lastError = recovery.exceptionOrNull()
                                lifecycle.publish(session) {
                                    diagnosticLog.error(LOG_SOURCE, "Monitor recovery failed.", lastError)
                                    stop(clearFailure = false)
                                    mutableFailure.value = lastError?.message ?: "AppOps service unavailable"
                                }
                                return@launch
                            }
                        }
                    }
                    // Every start is checked here, including a restored session,
                    // rather than only an explicit enable.
                    checkRegistration()
                    result
                })
            } catch (cancelled: CancellationException) {
                lifecycle.whenStopped {
                    mutableStatus.value = null
                    sessionData = null
                    notifier.cancel()
                }
                throw cancelled
            } catch (error: Exception) {
                lifecycle.whenStopped {
                    mutableStatus.value = null
                    mutableFailure.value = error.message ?: error::class.java.simpleName
                }
                diagnosticLog.error(LOG_SOURCE, "Unable to register the AppOps monitor.", error)
                Result.failure(error)
            }
        }

    fun refreshNotifications() {
        val session = lifecycle.session() ?: return
        lifecycle.publish(session) { sessionData?.let(::postQuietly) }
    }

    private fun postQuietly(data: SessionData) {
        postNotification(data, alert = false)
    }

    private fun postNotification(data: SessionData, alert: Boolean) {
        try {
            if (data.accumulator.accesses.isEmpty()) notifier.postOngoing(data.alertChannel, data.pointCount)
            else notifier.notifyAccesses(data.accumulator.accesses, data.alertChannel, alert)
        } catch (error: Exception) {
            diagnosticLog.error(LOG_SOURCE, "Monitor notification failed.", error)
            stop(clearFailure = false)
            mutableFailure.value = error.message ?: "Unable to update monitor notification"
        }
    }

    fun clearAccesses() {
        lifecycle.clear {
            sessionData?.let { it.accumulator.clear(); postQuietly(it) }
        }
    }

    fun stop(clearFailure: Boolean = true) {
        lifecycle.stop {
            mutableStatus.value = null
            if (clearFailure) mutableFailure.value = null
            sessionData = null
            notifier.cancel()
        }
    }

    suspend fun runSelfCheck(): MonitorSelfCheckResult {
        val registration = start()
        registration.exceptionOrNull()?.let {
            return MonitorSelfCheckResult.Failed(it.message ?: it::class.java.simpleName)
        }
        // start() has already compared the registration with the registry.
        val status = mutableStatus.value
        status?.callbackFailure?.let { return MonitorSelfCheckResult.Failed(it) }
        return when (status?.registry) {
            WatchRegistration.CONFIRMED -> MonitorSelfCheckResult.Passed
            else -> MonitorSelfCheckResult.Unconfirmed
        }
    }

    private suspend fun checkRegistration(): WatchRegistration {
        val session = lifecycle.session() ?: return WatchRegistration.UNKNOWN
        val expected = lifecycle.publish(session) {
            Triple(sessionData?.operationNames, mutableStatus.value, sessionData?.vendorOutcomes.orEmpty())
        } ?: return WatchRegistration.UNKNOWN
        val status = expected.second ?: return WatchRegistration.UNKNOWN
        val vendorOutcomes = expected.third
        val kinds = buildSet {
            if (status.activeWatch) add(AccessWatchKind.ACTIVE)
            if (status.startedWatch) add(AccessWatchKind.STARTED)
            if (status.notedWatch) add(AccessWatchKind.NOTED)
        }
        // The private watches are read from the same dump but never decide the
        // result: the registry check, and with it the watchdog, covers the
        // standard watches only.
        var vendorRegistry: Map<AccessWatchKind, Set<Int>>? = null
        val result = try {
            val snapshot = watchers.read()
            if (vendorOutcomes.isNotEmpty()) vendorRegistry = snapshot.vendorCodes(Shizuku.getUid())
            snapshot.registration(expected.first.orEmpty(), kinds, Shizuku.getUid()).also {
                diagnosticLog.info(LOG_SOURCE, "Watcher registry=$it, expected=${expected.first}, " +
                    "kinds=$kinds, registrations=${snapshot.accessWatchers}")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            diagnosticLog.warning(LOG_SOURCE, "Watcher registry unavailable: ${error.message}")
            WatchRegistration.UNKNOWN
        }
        val vendorOps = if (vendorOutcomes.isEmpty()) {
            emptyList()
        } else {
            VendorWatches.evaluate(vendorOutcomes, vendorRegistry).also { watches ->
                diagnosticLog.info(
                    LOG_SOURCE,
                    "MIUI watch registry=${vendorRegistry?.mapKeys { it.key.name.lowercase() } ?: "unreadable"}\n" +
                        watches.joinToString("\n", transform = VendorWatches::describe),
                )
            }
        }
        lifecycle.publish(session) {
            mutableStatus.value = mutableStatus.value?.copy(registry = result, vendorOps = vendorOps)
            if (result == WatchRegistration.CONFIRMED) sessionData?.registryConfirmed = true
        }
        return result
    }

    /** Which private codes went to which watch and what the system said, for reports from the field. */
    private fun logVendorRegistration(registration: AppOpsMonitorClient.Registration) {
        if (registration.vendor.isEmpty()) return
        registration.vendorBatchFailures.forEach { failure ->
            diagnosticLog.warning(LOG_SOURCE, "MIUI watch batch refused, retrying each code alone: $failure")
        }
        registration.vendor.forEach { outcome ->
            val watch = "MIUI watch ${outcome.kind.name.lowercase()} ${outcome.codes}"
            if (outcome.failure == null) {
                diagnosticLog.info(LOG_SOURCE, "$watch: accepted")
            } else {
                diagnosticLog.warning(LOG_SOURCE, "$watch: refused: ${outcome.failure}")
            }
        }
    }

    /** The first event of each private code shows that the system really reports it. */
    private fun logFirstVendorEvent(
        session: MonitorLifecycle.Session,
        data: SessionData,
        event: AppOpAccessEvent,
    ) {
        val first = lifecycle.publish(session) { data.loggedVendorCodes.add(event.opCode) } ?: return
        if (first) {
            diagnosticLog.info(
                LOG_SOURCE,
                "MIUI op ${event.opCode} reported: kind=${event.kind.name.lowercase()}, " +
                    "package=${event.packageName}, allowed=${event.allowed}",
            )
        }
    }

    private companion object {
        const val LOG_SOURCE = "Monitor"
        const val WATCHDOG_INTERVAL_MILLIS = 5 * 60_000L
        const val NOTIFY_INTERVAL_MILLIS = 500L
        const val EVENT_QUEUE_CAPACITY = 256
    }
}
