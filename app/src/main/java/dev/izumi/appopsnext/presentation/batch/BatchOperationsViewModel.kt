package dev.izumi.appopsnext.presentation.batch

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.izumi.appopsnext.AppOpsNextApplication
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.appops.AdaptiveScopeModeChangeExecutor
import dev.izumi.appopsnext.appops.RevokedCompatRetry
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpIdentifier
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.appops.model.AppOpNames
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.batch.BatchAppOpsExecutor
import dev.izumi.appopsnext.batch.BatchRetry
import dev.izumi.appopsnext.batch.model.BatchOperationReport
import dev.izumi.appopsnext.batch.model.BatchOperationTarget
import dev.izumi.appopsnext.guard.GuardMode
import dev.izumi.appopsnext.guard.GuardRule
import dev.izumi.appopsnext.guard.GuardWriteTarget
import dev.izumi.appopsnext.templates.model.PermissionTemplate
import dev.izumi.appopsnext.templates.NewAppPolicyTemplate
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BatchOperationsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository =
        getApplication<AppOpsNextApplication>().appOpsRepository
    private val guardRepository =
        getApplication<AppOpsNextApplication>().guardRepository
    private val guardController =
        getApplication<AppOpsNextApplication>().guardController

    private val adaptiveScopeExecutor = AdaptiveScopeModeChangeExecutor(
        writeMemory = getApplication<AppOpsNextApplication>().revokedCompatWriteMemory,
    ) { uid ->
        // Resolves UIDs of other users too, which the app's package manager cannot.
        getApplication<AppOpsNextApplication>().userAppsRepository.packagesForUid(uid)
    }
    private suspend fun applyTarget(
        target: BatchOperationTarget,
        changed: MutableList<GuardRule>,
    ): AppOpModeChangeResult {
        val operation = AppOpIdentifier(
            stableName = target.stableOperationName,
            shellName = AppOpNames.shellName(target.stableOperationName),
        )
        return repository.withWriteTransaction { transaction ->
            val outcome = adaptiveScopeExecutor.execute(
                packageName = target.packageName,
                uid = target.uid,
                preferredScope = target.preferredScope,
                requestedMode = target.requestedMode,
                readMode = { scope ->
                    repository.readMode(
                        packageName = target.packageName,
                        operation = operation,
                        scope = scope,
                        userId = target.userId,
                        uid = target.uid,
                    )
                },
                revokedCompatRetry = RevokedCompatRetry(
                    operationName = target.stableOperationName,
                ) { permissionName ->
                    transaction.applyRevokedCompat(
                        packageName = target.packageName,
                        uid = target.uid,
                        operation = operation,
                        permissionName = permissionName,
                        requestedMode = target.requestedMode,
                        userId = target.userId,
                    )
                },
            ) { scope ->
                transaction.applyMode(
                    packageName = target.packageName,
                    operation = operation,
                    scope = scope,
                    requestedMode = target.requestedMode,
                    userId = target.userId,
                )
            }
            val result = outcome.result
            if (target.userId == null && result is AppOpModeChangeResult.Success) {
                changed += GuardRule(
                    packageName = target.packageName,
                    uid = target.uid,
                    stableOperationName = operation.stableName,
                    scope = outcome.appliedScope,
                    expectedMode = result.appliedMode,
                    autoRestore = false,
                )
            }
            result
        }
    }
    private val mutableUiState =
        MutableStateFlow<BatchOperationUiState>(BatchOperationUiState.Idle)
    val uiState: StateFlow<BatchOperationUiState> =
        mutableUiState.asStateFlow()

    fun requestTemplateApplication(
        template: PermissionTemplate,
        apps: List<InstalledApp>,
    ) {
        if (apps.isEmpty() || template.rules.isEmpty()) return
        viewModelScope.launch { confirmTemplate(template, apps) }
    }

    private suspend fun confirmTemplate(
        template: PermissionTemplate,
        apps: List<InstalledApp>,
    ) {
        val targets = apps.flatMap { app ->
            template.rules.map { rule ->
                BatchOperationTarget(
                    packageName = app.packageName,
                    appLabel = app.label,
                    uid = app.uid,
                    stableOperationName = rule.stableOperationName,
                    preferredScope = AppOpScope.PACKAGE,
                    requestedMode = rule.mode,
                    userId = app.userId,
                )
            }
        }
        mutableUiState.value = BatchOperationUiState.Confirming(
            BatchOperationRequest(
                title = if (NewAppPolicyTemplate.isBuiltIn(template.id)) {
                    getApplication<Application>().getString(
                        R.string.template_new_app_policy_title,
                    )
                } else {
                    template.name
                },
                targetCount = apps.size,
                operationCount = targets.size,
                targets = targets,
                offerGuard = guardRepository.mode.first() != GuardMode.OFF &&
                    apps.all { it.userId == null },
            ),
        )
    }

    fun requestPermissionBatch(
        app: InstalledApp,
        permissions: List<PermissionBatchSelection>,
        requestedMode: AppOpMode,
    ) {
        if (permissions.isEmpty()) return
        val targets = permissions.map { permission ->
            BatchOperationTarget(
                packageName = app.packageName,
                appLabel = app.label,
                uid = app.uid,
                stableOperationName = AppOpNames.stableName(
                    permission.operationName,
                ),
                preferredScope = permission.scope,
                requestedMode = requestedMode,
                userId = app.userId,
            )
        }
        mutableUiState.value = BatchOperationUiState.Confirming(
            BatchOperationRequest(
                title = app.label,
                targetCount = 1,
                operationCount = targets.size,
                targets = targets,
            ),
        )
    }

    fun requestOperationBatch(title: String, targets: List<BatchOperationTarget>) {
        if (targets.isEmpty() || mutableUiState.value !is BatchOperationUiState.Idle) return
        val affectedPackages = unselectedSharedUidPackages(targets) { uid ->
            getApplication<AppOpsNextApplication>().userAppsRepository.packagesForUid(uid)
        }
        // A UID write already covers every selected sibling. Execute it once.
        val distinctTargets = targets.distinctBy {
            if (it.preferredScope == AppOpScope.UID) "uid:${it.uid}" else "pkg:${it.packageName}"
        }
        mutableUiState.value = BatchOperationUiState.Confirming(
            BatchOperationRequest(
                title = title,
                targetCount = targets.size,
                operationCount = distinctTargets.size,
                targets = distinctTargets,
                previewTargets = targets,
                affectedPackages = affectedPackages,
            ),
        )
    }

    /**
     * Unlike [requestOperationBatch], the targets may name several operations
     * of one app, so none is folded into another.
     */
    fun requestSettingsBatch(title: String, targets: List<BatchOperationTarget>) {
        if (targets.isEmpty() || mutableUiState.value !is BatchOperationUiState.Idle) return
        mutableUiState.value = BatchOperationUiState.Confirming(
            BatchOperationRequest(
                title = title,
                targetCount = targets.distinctBy { it.userId to it.packageName }.size,
                operationCount = targets.size,
                targets = targets,
            ),
        )
    }

    /** With [guard] every setting the batch wrote is guarded at its new mode. */
    fun confirm(guard: Boolean = false) {
        val request =
            (mutableUiState.value as? BatchOperationUiState.Confirming)
                ?.request
                ?: return
        mutableUiState.value = BatchOperationUiState.Running(
            request = request,
            completed = 0,
        )
        viewModelScope.launch {
            val report = executeBatch(request, guard)
            guardRetried = guard
            mutableUiState.value = BatchOperationUiState.Finished(report, retryable = true)
        }
    }

    /** The guard choice of the batch on screen, which a retry of its failures keeps. */
    private var guardRetried = false

    /** Runs the failed changes of the finished batch again and folds the outcomes into its report. */
    fun retryFailures() {
        val finished = mutableUiState.value as? BatchOperationUiState.Finished ?: return
        if (!finished.retryable) return
        val failed = BatchRetry.failedTargets(finished.report)
        if (failed.isEmpty()) return
        val request = BatchOperationRequest(
            title = finished.report.title,
            targetCount = failed.distinctBy { it.userId to it.packageName }.size,
            operationCount = failed.size,
            targets = failed,
        )
        mutableUiState.value = BatchOperationUiState.Running(request = request, completed = 0)
        viewModelScope.launch {
            val retried = executeBatch(request, guardRetried)
            mutableUiState.value = BatchOperationUiState.Finished(
                report = BatchRetry.merge(finished.report, retried),
                retryable = true,
            )
        }
    }

    private suspend fun executeBatch(request: BatchOperationRequest, guard: Boolean): BatchOperationReport {
        // The guard watches the user this app runs in only.
        val targets = request.targets.filter { it.userId == null }.map {
            GuardWriteTarget(it.packageName, it.uid, it.stableOperationName)
        }
        return guardController.withUserWrites(targets) {
            val changed = mutableListOf<GuardRule>()
            val executor = BatchAppOpsExecutor { applyTarget(it, changed) }
            try {
                executor.execute(request.title, request.targets) { completed, _ ->
                    mutableUiState.value = BatchOperationUiState.Running(request, completed)
                }
            } finally {
                // Save completed writes even if the batch was cancelled partway through.
                if (changed.isNotEmpty()) {
                    withContext(NonCancellable) { guardRepository.applyBatch(changed, guardNew = guard) }
                }
            }
        }
    }

    private var deferredReport: BatchOperationReport? = null

    fun showReport(report: BatchOperationReport) {
        if (mutableUiState.value is BatchOperationUiState.Idle) {
            mutableUiState.value = BatchOperationUiState.Finished(report)
        } else {
            deferredReport = report
        }
    }

    fun dismiss() {
        if (mutableUiState.value !is BatchOperationUiState.Running) {
            mutableUiState.value = deferredReport?.let { BatchOperationUiState.Finished(it) }
                ?: BatchOperationUiState.Idle
            deferredReport = null
        }
    }
}
