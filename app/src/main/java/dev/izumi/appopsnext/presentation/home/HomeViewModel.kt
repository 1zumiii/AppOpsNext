package dev.izumi.appopsnext.presentation.home

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.izumi.appopsnext.AppOpsNextApplication
import dev.izumi.appopsnext.appops.AppOpsRepository
import dev.izumi.appopsnext.appops.model.AppOpsReadState
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
    ) { shizukuState, serviceState, readState ->
        HomeUiState(
            device = device,
            shizukuState = shizukuState,
            privilegedServiceState = serviceState,
            appOpsReadState = readState,
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
                    appOpsRepository.readPackageOps(
                        packageName = application.packageName,
                        uid = application.applicationInfo.uid,
                    )
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

    override fun onCleared() {
        privilegedServiceClient.disconnect()
        shizukuController.stop()
        super.onCleared()
    }
}
