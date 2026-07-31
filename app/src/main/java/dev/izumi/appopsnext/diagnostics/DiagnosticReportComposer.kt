package dev.izumi.appopsnext.diagnostics

import dev.izumi.appopsnext.appops.model.AppOpsReadState
import dev.izumi.appopsnext.shizuku.model.PrivilegedServiceState
import dev.izumi.appopsnext.shizuku.model.ShizukuState

object DiagnosticReportComposer {
    fun compose(
        environment: DiagnosticEnvironment,
        shizukuState: ShizukuState,
        privilegedServiceState: PrivilegedServiceState,
        appOpsReadState: AppOpsReadState,
        eventLines: List<String>,
    ): String = buildString {
        appendLine("AppOpsNext diagnostic report")
        appendLine(
            "Privacy reminder: review and redact this report before sharing.",
        )
        appendLine()
        appendLine("[App]")
        appendLine(
            "version=${environment.appVersionName} " +
                "(${environment.appVersionCode})",
        )
        appendLine("buildType=${environment.buildType}")
        appendLine("targetSdk=${environment.targetSdk}")
        appendLine("compileSdk=${environment.compileSdk}")
        appendLine("userHandle=${environment.userHandle}")
        appendLine("processUid=${environment.processUid}")
        appendLine("locale=${environment.locale}")
        appendLine()
        appendLine("[Device]")
        appendLine("manufacturer=${environment.manufacturer}")
        appendLine("model=${environment.model}")
        appendLine("device=${environment.device}")
        appendLine(
            "android=${environment.androidVersion} " +
                "(API ${environment.apiLevel})",
        )
        appendLine("securityPatch=${environment.securityPatch}")
        appendLine("buildId=${environment.buildId}")
        appendLine("abis=${environment.supportedAbis}")
        appendLine()
        appendLine("[Shizuku]")
        appendLine("clientApi=${environment.shizukuApiVersion}")
        appendLine("manager=${environment.shizukuManagerVersion}")
        appendLine("state=${describe(shizukuState)}")
        appendLine()
        appendLine("[AppOps backend]")
        appendLine("backend=${describe(privilegedServiceState)}")
        appendLine("selfCheck=${describe(appOpsReadState)}")
        appendLine()
        appendLine("[Events]")
        if (eventLines.isEmpty()) {
            appendLine("No diagnostic events recorded.")
        } else {
            eventLines.forEach(::appendLine)
        }
    }.trimEnd()

    private fun describe(state: ShizukuState): String =
        when (state) {
            ShizukuState.Checking -> "checking"
            is ShizukuState.Unavailable ->
                "unavailable(installed=${state.isInstalled})"
            ShizukuState.Unsupported -> "unsupported"
            ShizukuState.PermissionRequired -> "permission_required"
            ShizukuState.PermissionDenied -> "permission_denied"
            is ShizukuState.Ready ->
                "ready(serverVersion=${state.serverVersion}," +
                    "uid=${state.serverUid})"
            is ShizukuState.Failure -> "failure(${state.reason})"
        }

    private fun describe(state: PrivilegedServiceState): String =
        when (state) {
            PrivilegedServiceState.Disconnected -> "disconnected"
            is PrivilegedServiceState.Connecting ->
                "connecting(type=${state.backendType.name.lowercase()})"
            is PrivilegedServiceState.Connected ->
                "connected(type=${state.info.backendType.name.lowercase()}," +
                    "uid=${state.info.uid},pid=${state.info.pid}," +
                    "api=${state.info.apiLevel})"
            is PrivilegedServiceState.Failure ->
                "failure(${state.reason})"
        }

    private fun describe(state: AppOpsReadState): String =
        when (state) {
            AppOpsReadState.WaitingForBackend -> "waiting_for_backend"
            AppOpsReadState.Reading -> "reading"
            is AppOpsReadState.Ready ->
                "ready(operationCount=${state.operationCount})"
            is AppOpsReadState.Failure -> "failure(${state.reason})"
        }
}
