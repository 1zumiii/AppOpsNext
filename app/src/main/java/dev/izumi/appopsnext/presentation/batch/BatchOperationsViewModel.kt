package dev.izumi.appopsnext.presentation.batch

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.izumi.appopsnext.AppOpsNextApplication
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.appops.AdaptiveScopeModeChangeExecutor
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpIdentifier
import dev.izumi.appopsnext.appops.model.AppOpNames
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.batch.BatchAppOpsExecutor
import dev.izumi.appopsnext.batch.model.BatchOperationReport
import dev.izumi.appopsnext.batch.model.BatchOperationTarget
import dev.izumi.appopsnext.templates.model.PermissionTemplate
import dev.izumi.appopsnext.templates.NewAppPolicyTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BatchOperationsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository =
        getApplication<AppOpsNextApplication>().appOpsRepository
    private val adaptiveScopeExecutor = AdaptiveScopeModeChangeExecutor { uid ->
        getApplication<Application>().packageManager
            .getPackagesForUid(uid)
            ?.toList()
            .orEmpty()
    }
    private val executor = BatchAppOpsExecutor { target ->
        repository.withWriteTransaction { transaction ->
            adaptiveScopeExecutor.execute(
                packageName = target.packageName,
                uid = target.uid,
                preferredScope = target.preferredScope,
                requestedMode = target.requestedMode,
                readMode = { scope ->
                    repository.readMode(
                        packageName = target.packageName,
                        operation = AppOpIdentifier(
                            stableName = target.stableOperationName,
                            shellName = AppOpNames.shellName(
                                target.stableOperationName,
                            ),
                        ),
                        scope = scope,
                    )
                },
            ) { scope ->
                transaction.applyMode(
                    packageName = target.packageName,
                    operation = AppOpIdentifier(
                        stableName = target.stableOperationName,
                        shellName = AppOpNames.shellName(
                            target.stableOperationName,
                        ),
                    ),
                    scope = scope,
                    requestedMode = target.requestedMode,
                )
            }.result
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

    fun confirm() {
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
            mutableUiState.value = BatchOperationUiState.Finished(report)
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
