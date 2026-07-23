package dev.izumi.appops.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import dev.izumi.appops.BuildConfig
import dev.izumi.appops.shizuku.model.PrivilegedServiceInfo
import dev.izumi.appops.shizuku.model.PrivilegedServiceFailureReason
import dev.izumi.appops.shizuku.model.PrivilegedServiceState
import dev.izumi.appops.shizuku.service.AppOpsUserService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

class PrivilegedServiceClient(
    context: Context,
) {
    private val mutableState =
        MutableStateFlow<PrivilegedServiceState>(PrivilegedServiceState.Disconnected)
    val state: StateFlow<PrivilegedServiceState> = mutableState.asStateFlow()

    private var service: IPrivilegedAppOpsService? = null
    private var bound = false

    private val userServiceArgs =
        Shizuku.UserServiceArgs(
            ComponentName(context.packageName, AppOpsUserService::class.java.name),
        )
            .daemon(false)
            .processNameSuffix(USER_SERVICE_PROCESS_SUFFIX)
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder?) {
            if (binder == null || !binder.pingBinder()) {
                service = null
                mutableState.value = PrivilegedServiceState.Failure(
                    PrivilegedServiceFailureReason.EMPTY_BINDER,
                )
                return
            }

            runCatching {
                IPrivilegedAppOpsService.Stub.asInterface(binder).also { connectedService ->
                    service = connectedService
                    mutableState.value = PrivilegedServiceState.Connected(
                        PrivilegedServiceInfo(
                            uid = connectedService.uid,
                            pid = connectedService.pid,
                            apiLevel = connectedService.apiLevel,
                        ),
                    )
                }
            }.onFailure { error ->
                Log.e(TAG, "Unable to initialize UserService", error)
                service = null
                mutableState.value = PrivilegedServiceState.Failure(
                    PrivilegedServiceFailureReason.INITIALIZATION_FAILED,
                )
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            bound = false
            mutableState.value = PrivilegedServiceState.Disconnected
        }
    }

    fun connect() {
        if (bound || mutableState.value is PrivilegedServiceState.Connecting) return

        mutableState.value = PrivilegedServiceState.Connecting
        runCatching {
            Shizuku.bindUserService(userServiceArgs, connection)
            bound = true
        }.onFailure { error ->
            Log.e(TAG, "Unable to bind UserService", error)
            bound = false
            mutableState.value = PrivilegedServiceState.Failure(
                PrivilegedServiceFailureReason.BIND_FAILED,
            )
        }
    }

    fun disconnect() {
        if (bound) {
            runCatching {
                Shizuku.unbindUserService(userServiceArgs, connection, false)
            }.onFailure { error ->
                Log.w(TAG, "Unable to unbind UserService", error)
            }
        }

        service = null
        bound = false
        mutableState.value = PrivilegedServiceState.Disconnected
    }

    private companion object {
        const val TAG = "PrivilegedService"
        const val USER_SERVICE_PROCESS_SUFFIX = "appops"
    }
}
