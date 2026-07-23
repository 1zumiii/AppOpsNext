package dev.izumi.appops.presentation.home

import dev.izumi.appops.model.DeviceSummary
import dev.izumi.appops.appops.model.AppOpsReadState
import dev.izumi.appops.shizuku.model.PrivilegedServiceState
import dev.izumi.appops.shizuku.model.ShizukuState

data class HomeUiState(
    val device: DeviceSummary,
    val shizukuState: ShizukuState = ShizukuState.Checking,
    val privilegedServiceState: PrivilegedServiceState =
        PrivilegedServiceState.Disconnected,
    val appOpsReadState: AppOpsReadState = AppOpsReadState.WaitingForBackend,
)
