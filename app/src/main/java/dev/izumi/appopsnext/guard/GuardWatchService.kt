package dev.izumi.appopsnext.guard

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import dev.izumi.appopsnext.AppOpsNextApplication
import dev.izumi.appopsnext.monitor.AppOpCodes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/**
 * The foreground service of [GuardMode.REALTIME]. It watches mode changes of
 * the guarded operations and checks the rules of an operation shortly after it
 * changes, so several changes in a row cost one check.
 */
class GuardWatchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val watch = GuardModeWatch()
    private val changes = Channel<Int>(Channel.UNLIMITED)
    private var watchedCodes: Set<Int> = emptySet()
    private var ruleCount = 0
    private val binderReceived = Shizuku.OnBinderReceivedListener { scope.launch { register(watchedCodes, force = true) } }
    private val binderDead = Shizuku.OnBinderDeadListener { watch.stop() }

    private val app get() = application as AppOpsNextApplication

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Shizuku.addBinderReceivedListener(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        scope.launch {
            app.guardRepository.rules
                .distinctUntilChanged()
                .collect { rules ->
                    ruleCount = rules.size
                    startForeground(
                        GuardNotifier.NOTIFICATION_STATUS,
                        app.guardNotifier.watchingNotification(ruleCount),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                    )
                    register(
                        rules.mapNotNullTo(mutableSetOf()) { AppOpCodes.codeOf(it.stableOperationName) },
                        force = false,
                    )
                }
        }
        scope.launch { consumeChanges() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The five-second foreground deadline cannot wait for the rules to load.
        startForeground(
            GuardNotifier.NOTIFICATION_STATUS,
            app.guardNotifier.watchingNotification(ruleCount),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        return START_STICKY
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        watch.stop()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun register(codes: Set<Int>, force: Boolean) {
        if (!force && codes == watchedCodes && watch.isAlive) return
        watchedCodes = codes
        val registered = runCatching {
            watch.watch(codes) { opCode, _, _ ->
                app.guardController.onModeCallback()
                changes.trySend(opCode)
            }
        }
        registered.exceptionOrNull()?.let { error ->
            app.diagnosticLogRepository.warning(
                LOG_SOURCE,
                "Mode watch not registered; waiting for Shizuku. ${error::class.java.simpleName}: ${error.message}",
            )
        }
        // A change made while nothing was watching is caught by a full check.
        if (registered.isSuccess) app.guardController.runCheckIfEnabled("realtime_start")
    }

    private suspend fun consumeChanges() {
        val pending = mutableSetOf<Int>()
        for (first in changes) {
            pending += first
            delay(DEBOUNCE_MILLIS)
            while (true) pending += changes.tryReceive().getOrNull() ?: break
            val operations = pending.mapNotNullTo(mutableSetOf(), AppOpCodes::nameOf)
            pending.clear()
            app.guardController.runCheckIfEnabled("realtime", operations)
        }
    }

    companion object {
        private const val LOG_SOURCE = "Guard"
        private const val DEBOUNCE_MILLIS = 1_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, GuardWatchService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GuardWatchService::class.java))
        }
    }
}
