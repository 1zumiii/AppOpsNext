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
import dev.izumi.appopsnext.batch.model.BatchOperationReport
import dev.izumi.appopsnext.batch.model.BatchOperationTarget
import dev.izumi.appopsnext.guard.GuardMode
import dev.izumi.appopsnext.guard.GuardRule
import dev.izumi.appopsnext.templates.model.PermissionTemplate
import dev.izumi.appopsnext.templates.NewAppPolicyTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BatchOperationsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository =
        getApplication<AppOpsNextApplication>().appOpsRepository
    private val guardRepository =
        getApplication<AppOpsNextApplication>().guardRepository

    /** The scope each target was written in, which can fall back from the preferred one. */
    private val appliedScopes = mutableMapOf<BatchOperationTarget, AppOpScope>()
    private val adaptiveScopeExecutor = AdaptiveScopeModeChangeExecutor(
        writeMemory = getApplication<AppOpsNextApplication>().revokedCompatWriteMemory,
    ) { uid ->
        getApplication<Application>().packageManager
            .getPackagesForUid(uid)
            ?.toList()
            .orEmpty()
    }
    private val executor = BatchAppOpsExecutor { target ->
        val operation = AppOpIdentifier(
            stableName = target.stableOperationName,
            shellName = AppOpNames.shellName(target.stableOperationName),
        )
        repository.withWriteTransaction { transaction ->
            adaptiveScopeExecutor.execute(
                packageName = target.packageName,
                uid = target.uid,
                preferredScope = target.preferredScope,
                requestedMode = target.requestedMode,
                readMode = { scope ->
                    repository.readMode(
                        packageName = target.packageName,
                        operation = operation,
                        scope = scope,
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
                    )
                },
            ) { scope ->
                transaction.applyMode(
                    packageName = target.packageName,
                    operation = operation,
                    scope = scope,
                    requestedMode = target.requestedMode,
                )
            }.also { appliedScopes[target] = it.appliedScope }.result
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
            getApplication<Application>().packageManager
                .getPackagesForUid(uid)
                ?.toList()
                .orEmpty()
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
            val report = executor.execute(
                title = request.title,
                targets = request.targets,
                onProgress = { completed, _ ->
                    mutableUiState.value = BatchOperationUiState.Running(
                        request = request,
                        completed = completed,
                    )
                },
            )
            recordGuardChanges(report, guard)
            mutableUiState.value = BatchOperationUiState.Finished(report)
        }
    }

    /** Keeps guarded settings at the mode the user just chose, so the guard does not take it for a drift. */
    private suspend fun recordGuardChanges(report: BatchOperationReport, guard: Boolean) {
        val changed = report.results.mapNotNull { item ->
            val success = item.result as? AppOpModeChangeResult.Success ?: return@mapNotNull null
            GuardRule(
                packageName = item.target.packageName,
                uid = item.target.uid,
                stableOperationName = item.target.stableOperationName,
                scope = appliedScopes[item.target] ?: item.target.preferredScope,
                expectedMode = success.appliedMode,
                autoRestore = false,
            )
        }
        appliedScopes.clear()
        if (changed.isNotEmpty()) guardRepository.applyBatch(changed, guardNew = guard)
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
