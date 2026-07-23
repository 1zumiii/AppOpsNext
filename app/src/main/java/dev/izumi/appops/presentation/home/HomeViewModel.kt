package dev.izumi.appops.presentation.home

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.izumi.appops.model.DeviceSummary
import dev.izumi.appops.shizuku.PrivilegedServiceClient
import dev.izumi.appops.shizuku.ShizukuController
import dev.izumi.appops.shizuku.model.ShizukuState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val shizukuController = ShizukuController(application)
    private val privilegedServiceClient = PrivilegedServiceClient(application)

    private val device = DeviceSummary(
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        androidVersion = Build.VERSION.RELEASE,
        apiLevel = Build.VERSION.SDK_INT,
    )

    val uiState = combine(
        shizukuController.state,
        privilegedServiceClient.state,
    ) { shizukuState, serviceState ->
        HomeUiState(
            device = device,
            shizukuState = shizukuState,
            privilegedServiceState = serviceState,
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

