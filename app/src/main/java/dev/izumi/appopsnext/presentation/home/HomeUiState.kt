package dev.izumi.appopsnext.presentation.home

import dev.izumi.appopsnext.appops.model.AppOpsReadState
import dev.izumi.appopsnext.appops.model.AppOpsWriteTestState
import dev.izumi.appopsnext.model.DeviceSummary
import dev.izumi.appopsnext.shizuku.model.PrivilegedServiceState
import dev.izumi.appopsnext.shizuku.model.ShizukuState

data class HomeUiState(
    val device: DeviceSummary,
    val shizukuState: ShizukuState = ShizukuState.Checking,
    val privilegedServiceState: PrivilegedServiceState =
        PrivilegedServiceState.Disconnected,
    val appOpsReadState: AppOpsReadState = AppOpsReadState.WaitingForBackend,
    val isTestTargetInstalled: Boolean = false,
    val appOpsWriteTestState: AppOpsWriteTestState = AppOpsWriteTestState.NotRun,
)
