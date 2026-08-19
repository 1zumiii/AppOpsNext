package dev.izumi.appopsnext.newapps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import dev.izumi.appopsnext.appops.AdaptiveScopeModeChangeOutcome
import dev.izumi.appopsnext.appops.AdaptiveScopeModeChangeExecutor
import dev.izumi.appopsnext.appops.AppOpsRepository
import dev.izumi.appopsnext.appops.model.AppOpIdentifier
import dev.izumi.appopsnext.appops.model.AppOpModeChangePhase
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.appops.model.AppOpNames
import dev.izumi.appopsnext.batch.BatchAppOpsExecutor
import dev.izumi.appopsnext.batch.model.BatchOperationReport
import dev.izumi.appopsnext.batch.model.BatchOperationTarget
import dev.izumi.appopsnext.diagnostics.DiagnosticLogRepository
import dev.izumi.appopsnext.newapps.model.InstalledPackageRecord
import dev.izumi.appopsnext.settings.UserSettingsRepository
import dev.izumi.appopsnext.shizuku.PrivilegedServiceClient
import dev.izumi.appopsnext.shizuku.model.PrivilegedServiceState
import dev.izumi.appopsnext.templates.NewAppPolicyTemplate
import dev.izumi.appopsnext.templates.PermissionTemplateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NewAppPolicyCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settingsRepository: UserSettingsRepository,
    private val stateRepository: NewAppPolicyStateRepository,
    private val templateRepository: PermissionTemplateRepository,
    private val privilegedServiceClient: PrivilegedServiceClient,
    private val diagnosticLog: DiagnosticLogRepository,
    private val scanner: InstalledPackageScanner =
        InstalledPackageScanner(context),
    private val notifier: NewAppPolicyNotifier =
        NewAppPolicyNotifier(context),
) {
    private val workMutex = Mutex()
    private val appOpsRepository = AppOpsRepository(privilegedServiceClient)
    private val adaptiveScopeExecutor = AdaptiveScopeModeChangeExecutor { uid ->
        context.packageManager.getPackagesForUid(uid)?.toList().orEmpty()
    }
    private val executor = BatchAppOpsExecutor { target ->
        val operation = AppOpIdentifier(
            stableName = target.stableOperationName,
            shellName = AppOpNames.shellName(target.stableOperationName),
        )
        val outcome = adaptiveScopeExecutor.execute(
            packageName = target.packageName,
            uid = target.uid,
            preferredScope = target.preferredScope,
            requestedMode = target.requestedMode,
            readMode = { scope ->
                appOpsRepository.readMode(
                    packageName = target.packageName,
                    operation = operation,
                    scope = scope,
                )
            },
        ) { scope ->
            appOpsRepository.applyMode(
                packageName = target.packageName,
                operation = operation,
                scope = scope,
                requestedMode = target.requestedMode,
            )
        }
        diagnosticLog.info(
            source = LOG_SOURCE,
            message = outcome.toDiagnosticMessage(target),
        )
        outcome.result
    }
    private var started = false

    private val packageAddedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (
                intent?.action != Intent.ACTION_PACKAGE_ADDED ||
                intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            ) {
                return
            }
            val packageName = intent.data?.schemeSpecificPart ?: return
            scope.launch {
                runSafely("package_added:$packageName") {
                    reconcileAndProcess("package_added:$packageName")
                }
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        runCatching {
            context.registerReceiver(
                packageAddedReceiver,
                IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply {
                    addDataScheme("package")
                },
                Context.RECEIVER_EXPORTED,
            )
        }.onFailure { error ->
            diagnosticLog.error(
                source = LOG_SOURCE,
                message =
                    "Unable to register the live package-added receiver. " +
                        "Foreground reconciliation remains available.",
                error = error,
            )
        }
        scope.launch {
            settingsRepository.settings
                .map { it.autoApplyNewAppTemplate }
                .distinctUntilChanged()
                .collect { enabled ->
                    runSafely("setting_changed") {
                        workMutex.withLock {
                            if (enabled) {
                                reconcileAndProcessLocked("setting_enabled")
                            } else {
                                stateRepository.reset()
                            }
                        }
                    }
                }
        }
        scope.launch {
            privilegedServiceClient.state
                .filterIsInstance<PrivilegedServiceState.Connected>()
                .collect {
                    runSafely("backend_connected") {
                        processPendingIfEnabled()
                    }
                }
        }
    }

    fun onAppForeground() {
        scope.launch {
            runSafely("app_foreground") {
                reconcileAndProcess("app_foreground")
            }
        }
    }

    private suspend fun reconcileAndProcess(reason: String) {
        if (!isEnabled()) return
        workMutex.withLock {
            reconcileAndProcessLocked(reason)
        }
    }

    private suspend fun reconcileAndProcessLocked(reason: String) {
        val installedApps = scanner.scanUserApps()
        val result = stateRepository.reconcile(
            installedApps.map { it.fingerprint },
        )
        if (result.initializedBaseline) {
            diagnosticLog.info(
                source = LOG_SOURCE,
                message =
                    "Initialized new-app baseline. " +
                        "count=${installedApps.size}, reason=$reason",
            )
        }
        if (result.detected.isNotEmpty()) {
            val detectedPackages = result.detected.joinToString { item ->
                item.packageName
            }
            diagnosticLog.info(
                source = LOG_SOURCE,
                message =
                    "Detected new user apps. count=${result.detected.size}, " +
                        "packages=$detectedPackages, " +
                        "reason=$reason",
            )
        }
        processPendingLocked(installedApps)
    }

    private suspend fun processPendingIfEnabled() {
        if (!isEnabled()) return
        workMutex.withLock {
            processPendingLocked(scanner.scanUserApps())
        }
    }

    private suspend fun processPendingLocked(
        installedApps: List<InstalledPackageRecord>,
    ) {
        if (
            privilegedServiceClient.state.value
            !is PrivilegedServiceState.Connected
        ) {
            return
        }
        val pending = stateRepository.pending()
        if (pending.isEmpty()) return
        val appsByFingerprint = installedApps.associateBy { it.fingerprint }
        val template = templateRepository.templates.first().first {
            NewAppPolicyTemplate.isBuiltIn(it.id)
        }

        for (fingerprint in pending) {
            if (!isEnabled()) return
            val app = appsByFingerprint[fingerprint]
            if (app == null) {
                diagnosticLog.warning(
                    source = LOG_SOURCE,
                    message =
                        "Skipping pending package because it is no longer " +
                            "installed. package=${fingerprint.packageName}",
                )
                stateRepository.markProcessed(fingerprint)
                continue
            }
            val targets = NewAppPolicyTargetFactory.create(app, template)
            if (targets.isEmpty()) {
                diagnosticLog.info(
                    source = LOG_SOURCE,
                    message =
                        "New-app template has no rules. " +
                            "package=${fingerprint.packageName}",
                )
                stateRepository.markProcessed(fingerprint)
                continue
            }

            val report = executor.execute(
                title = NewAppPolicyTemplate.INTERNAL_NAME,
                targets = targets,
            )
            if (report.isBackendUnavailable()) {
                diagnosticLog.warning(
                    source = LOG_SOURCE,
                    message =
                        "New-app policy is waiting for the backend. " +
                            "package=${fingerprint.packageName}",
                )
                return
            }
            diagnosticLog.info(
                source = LOG_SOURCE,
                message =
                    "New-app policy completed. " +
                        "package=${fingerprint.packageName}, " +
                        "success=${report.successCount}, " +
                        "failure=${report.failureCount}",
            )
            notifier.notifyCompleted(
                packageName = fingerprint.packageName,
                appLabel = app.label,
                successCount = report.successCount,
                failureCount = report.failureCount,
            )
            stateRepository.markProcessed(fingerprint)
        }
    }

    private suspend fun isEnabled(): Boolean =
        settingsRepository.settings.first().autoApplyNewAppTemplate

    private suspend fun runSafely(
        reason: String,
        action: suspend () -> Unit,
    ) {
        try {
            action()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            diagnosticLog.error(
                source = LOG_SOURCE,
                message = "New-app policy work failed. reason=$reason",
                error = error,
            )
        }
    }

    private fun BatchOperationReport.isBackendUnavailable(): Boolean =
        results.isNotEmpty() && results.all { item ->
            val failure = item.result as? AppOpModeChangeResult.Failure
                ?: return@all false
            failure.phase == AppOpModeChangePhase.READ_ORIGINAL &&
                failure.originalMode == null
        }

    private fun AdaptiveScopeModeChangeOutcome.toDiagnosticMessage(
        target: BatchOperationTarget,
    ): String {
        val resultSummary = when (val modeResult = result) {
            is AppOpModeChangeResult.Success ->
                "success:${modeResult.appliedMode.name}"

            is AppOpModeChangeResult.Failure ->
                "failure:${modeResult.phase.name}:" +
                    modeResult.restorationStatus.name
        }
        return "New-app rule completed. " +
            "package=${target.packageName}, " +
            "operation=${target.stableOperationName}, " +
            "preferredScope=${target.preferredScope.name}, " +
            "resolvedScope=${appliedScope.name}, " +
            "fallback=$fallbackAttempted, result=$resultSummary"
    }

    private companion object {
        const val LOG_SOURCE = "NewAppPolicy"
    }
}
