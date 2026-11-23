package dev.izumi.appopsnext.temporary

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import dev.izumi.appopsnext.appops.AppOpsRepository
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpIdentifier
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.appops.model.AppOpNames
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.diagnostics.DiagnosticLogRepository
import dev.izumi.appopsnext.guard.GuardController
import dev.izumi.appopsnext.guard.GuardWriteTarget
import dev.izumi.appopsnext.monitor.ForegroundStateProbe
import dev.izumi.appopsnext.shizuku.PrivilegedServiceClient
import dev.izumi.appopsnext.shizuku.model.PrivilegedServiceState
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface TemporaryAllowResult {
    data class Write(val result: AppOpModeChangeResult, val scope: AppOpScope) : TemporaryAllowResult
    enum class Unavailable : TemporaryAllowResult { ALREADY_ACTIVE, DISCONNECTED, SCOPE_CHANGED, MONITOR_UNAVAILABLE }
}

internal class TemporaryAllowController(
    private val context: Context,
    private val repository: TemporaryAllowRepository,
    private val appOps: AppOpsRepository,
    private val guard: GuardController,
    private val backend: PrivilegedServiceClient,
    private val diagnosticLog: DiagnosticLogRepository,
    private val applicationScope: CoroutineScope,
) {
    private val foreground = ForegroundStateProbe(backend)
    private val writer = TemporaryAllowWriter(packagesForUid = ::packagesForUid)
    private val power = context.getSystemService(PowerManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val mutableRestores = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val restores = mutableRestores.asSharedFlow()
    val active = repository.active.catch { error ->
        if (error !is IOException) throw error
        diagnosticLog.error(TAG, "Could not read temporary permissions.", error)
    }

    fun start() {
        applicationScope.launch {
            backend.state.filterIsInstance<PrivilegedServiceState.Connected>().collect { check() }
        }
    }

    fun onAppForeground() {
        applicationScope.launch {
            try {
                if (repository.entries().isNotEmpty()) {
                    TemporaryAllowService.start(context)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                diagnosticLog.error(TAG, "Could not restart temporary permission monitoring.", error)
            }
        }
    }

    suspend fun allow(
        target: GuardWriteTarget,
        scope: AppOpScope,
        shownOriginal: AppOpMode,
    ): TemporaryAllowResult = try {
        if (backend.ensureConnected()) {
            guard.withUserWrites(listOf(target)) { allowGuarded(target, scope, shownOriginal) }
        } else {
            TemporaryAllowResult.Unavailable.DISCONNECTED
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        diagnosticLog.error(TAG, "Temporary permission write failed.", error)
        TemporaryAllowResult.Unavailable.MONITOR_UNAVAILABLE
    }

    private suspend fun allowGuarded(
        target: GuardWriteTarget,
        scope: AppOpScope,
        shownOriginal: AppOpMode,
    ): TemporaryAllowResult {
        val packages = packagesForUid(target.uid)
        if (target.packageName !in packages || (scope == AppOpScope.UID && packages.size != 1)) {
            return TemporaryAllowResult.Unavailable.SCOPE_CHANGED
        }
        if (repository.entries().any { it.target == target }) return TemporaryAllowResult.Unavailable.ALREADY_ACTIVE

        return appOps.withWriteTransaction { transaction ->
            // Persist the recovery work and the original mode before changing anything.
            // A process death after the write can then recover from the same record.
            withContext(NonCancellable) {
                writer.allow(
                    target = target,
                    preferredScope = scope,
                    shownOriginal = shownOriginal,
                    readMode = { readMode(target, it) },
                    stage = ::prepareRecovery,
                    clear = repository::remove,
                    save = repository::put,
                ) { actualScope, original, acceptUidDefault ->
                    transaction.changeMode(
                        target.packageName, target.identifier, actualScope, original, AppOpMode.ALLOW,
                        requireExactOriginalMode = true,
                        acceptUidDefaultForAllow = acceptUidDefault,
                    )
                }
            }
        }
    }

    /** True while a foreground or worker check still needs to be scheduled. */
    suspend fun check(): Boolean {
        try {
            val snapshot = repository.entries()
            if (snapshot.isEmpty()) return false
            val connected = backend.ensureConnected()
            for ((target) in snapshot) {
                val finished = guard.withUserWrites(listOf(target)) { checkTarget(target, connected) }
                if (finished) onFinished(target)
            }
            return repository.entries().isNotEmpty()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            diagnosticLog.error(TAG, "Temporary permission check failed.", error)
            return true
        }
    }

    fun revoke(entry: TemporaryAllow) {
        applicationScope.launch {
            val target = entry.target
            try {
                val finished = guard.withUserWrites(listOf(target)) {
                    val current = repository.entries().firstOrNull {
                        it.target == target && it.scope == entry.scope && it.openByMillis == entry.openByMillis
                    } ?: return@withUserWrites false
                    val restoring = current.restoring()
                    if (restoring != current) repository.put(restoring)
                    checkTarget(target, backend.ensureConnected())
                }
                if (finished) onFinished(target)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                diagnosticLog.error(TAG, "Could not revoke temporary permission.", error)
            }
        }
    }

    private fun onFinished(target: GuardWriteTarget) {
        mutableRestores.tryEmit(Unit)
        applicationScope.launch {
            guard.runCheckIfEnabled("temporary_allow_finished", setOf(target.stableOperationName))
        }
    }

    private suspend fun checkTarget(target: GuardWriteTarget, connected: Boolean): Boolean {
        // The service, worker and permanent writes may all have acted since the snapshot.
        val entry = repository.entries().firstOrNull { it.target == target } ?: return false
        if (target.packageName !in packagesForUid(target.uid)) {
            repository.remove(entry)
            return true
        }
        val onScreen = when {
            entry.phase == TemporaryAllow.Phase.RESTORING -> null
            !power.isInteractive || keyguard.isDeviceLocked -> false
            connected -> foreground.onScreenState(target.uid, target.packageName)
            else -> null
        }
        val updated = entry.advance(onScreen, System.currentTimeMillis())
        if (updated != entry) repository.put(updated)
        if (updated.phase != TemporaryAllow.Phase.RESTORING || !connected) return false

        return appOps.withWriteTransaction { transaction ->
            val observed = readMode(target, updated.scope)
            val finished = updated.restoreIfStillAllowed(
                observed,
                readPackageMode = { readMode(target, AppOpScope.PACKAGE) },
            ) { scope, original ->
                transaction.changeMode(
                    target.packageName, target.identifier, scope, requireNotNull(observed), original,
                    requireExactOriginalMode = true,
                ) is AppOpModeChangeResult.Success
            }
            if (finished) repository.remove(updated)
            finished
        }
    }

    private suspend fun prepareRecovery(entry: TemporaryAllow): Boolean {
        try {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<TemporaryAllowWorker>()
                    .setInitialDelay(30, TimeUnit.SECONDS)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                    .build(),
            ).await()
            repository.put(entry)
            TemporaryAllowService.start(context)
            return true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            diagnosticLog.error(TAG, "Could not arrange temporary permission recovery.", error)
            repository.remove(entry)
            return false
        }
    }

    private fun packagesForUid(uid: Int): List<String> =
        context.packageManager.getPackagesForUid(uid)?.toList().orEmpty()

    private suspend fun readMode(target: GuardWriteTarget, scope: AppOpScope): AppOpMode? =
        appOps.readMode(target.packageName, target.identifier, scope, uid = target.uid)

    private val GuardWriteTarget.identifier: AppOpIdentifier
        get() = AppOpIdentifier(stableOperationName, AppOpNames.shellName(stableOperationName))

    private companion object {
        const val TAG = "TemporaryAllow"
        const val WORK_NAME = "temporary_allow_check"
    }
}
