package dev.izumi.appopsnext.presentation.home

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.izumi.appopsnext.AppOpsNextApplication
import dev.izumi.appopsnext.appops.AppOpsRepository
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpsReadState
import dev.izumi.appopsnext.appops.model.AppOpsWriteTestState
import dev.izumi.appopsnext.appops.testing.AppOpsTestTarget
import dev.izumi.appopsnext.model.DeviceSummary
import dev.izumi.appopsnext.shizuku.ShizukuController
import dev.izumi.appopsnext.shizuku.model.PrivilegedServiceState
import dev.izumi.appopsnext.shizuku.model.ShizukuState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val shizukuController = ShizukuController(application)
    private val privilegedServiceClient =
        getApplication<AppOpsNextApplication>().privilegedServiceClient
    private val appOpsRepository = AppOpsRepository(privilegedServiceClient)
    private val appOpsReadState =
        MutableStateFlow<AppOpsReadState>(AppOpsReadState.WaitingForBackend)
    private val isTestTargetInstalled =
        MutableStateFlow(queryTestTargetInstalled())
    private val appOpsWriteTestState =
        MutableStateFlow<AppOpsWriteTestState>(AppOpsWriteTestState.NotRun)

    private val device = DeviceSummary(
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        androidVersion = Build.VERSION.RELEASE,
        apiLevel = Build.VERSION.SDK_INT,
    )

    val uiState = combine(
        shizukuController.state,
        privilegedServiceClient.state,
        appOpsReadState,
        isTestTargetInstalled,
        appOpsWriteTestState,
    ) { shizukuState, serviceState, readState, targetInstalled, writeTestState ->
        HomeUiState(
            device = device,
            shizukuState = shizukuState,
            privilegedServiceState = serviceState,
            appOpsReadState = readState,
            isTestTargetInstalled = targetInstalled,
            appOpsWriteTestState = writeTestState,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeUiState(device = device),
    )

    init {
        shizukuController.start()
        viewModelScope.launch {
            shizukuController.state.collect { state ->
                if (state is ShizukuState.Ready) {
                    privilegedServiceClient.connect()
                } else {
                    privilegedServiceClient.disconnect()
                }
            }
        }
        viewModelScope.launch {
            privilegedServiceClient.state.collect { state ->
                appOpsReadState.value = if (state is PrivilegedServiceState.Connected) {
                    appOpsRepository.readPackageOps(application.packageName)
                } else {
                    AppOpsReadState.WaitingForBackend
                }
            }
        }
    }

    fun performShizukuAction() {
        when (shizukuController.state.value) {
            ShizukuState.PermissionRequired,
            ShizukuState.PermissionDenied,
            -> shizukuController.requestPermission()

            else -> shizukuController.refresh()
        }
    }

    fun performAppOpsWriteTest() {
        val targetInstalled = queryTestTargetInstalled()
        isTestTargetInstalled.value = targetInstalled
        if (!targetInstalled) return
        if (privilegedServiceClient.state.value !is PrivilegedServiceState.Connected) return
        if (appOpsWriteTestState.value is AppOpsWriteTestState.Running) return

        appOpsWriteTestState.value = AppOpsWriteTestState.Running
        viewModelScope.launch {
            appOpsWriteTestState.value = appOpsRepository.runModeRoundTrip(
                packageName = AppOpsTestTarget.PACKAGE_NAME,
                operation = AppOpsTestTarget.runInBackgroundOperation,
                testMode = AppOpMode.IGNORE,
            )
        }
    }

    private fun queryTestTargetInstalled(): Boolean =
        runCatching {
            getApplication<Application>().packageManager.getPackageInfo(
                AppOpsTestTarget.PACKAGE_NAME,
                PackageManager.PackageInfoFlags.of(0),
            )
        }.isSuccess

    override fun onCleared() {
        privilegedServiceClient.disconnect()
        shizukuController.stop()
        super.onCleared()
    }
}
