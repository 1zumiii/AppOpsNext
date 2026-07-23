package dev.izumi.appopsnext.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import dev.izumi.appopsnext.shizuku.model.ShizukuState
import dev.izumi.appopsnext.shizuku.model.ShizukuFailureReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

class ShizukuController(
    context: Context,
) {
    private val packageManager = context.packageManager

    private val mutableState = MutableStateFlow<ShizukuState>(ShizukuState.Checking)
    val state: StateFlow<ShizukuState> = mutableState.asStateFlow()

    private var started = false

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refresh()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        mutableState.value = ShizukuState.Unavailable(isShizukuInstalled())
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != PERMISSION_REQUEST_CODE) return@OnRequestPermissionResultListener

            mutableState.value = if (grantResult == PackageManager.PERMISSION_GRANTED) {
                readReadyState()
            } else {
                ShizukuState.PermissionDenied
            }
        }

    fun start() {
        if (started) return
        started = true
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        refresh()
    }

    fun stop() {
        if (!started) return
        started = false
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
    }

    fun refresh() {
        mutableState.value = runCatching {
            when {
                !Shizuku.pingBinder() -> ShizukuState.Unavailable(isShizukuInstalled())
                Shizuku.isPreV11() -> ShizukuState.Unsupported
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED ->
                    readReadyState()

                Shizuku.shouldShowRequestPermissionRationale() ->
                    ShizukuState.PermissionDenied

                else -> ShizukuState.PermissionRequired
            }
        }.getOrElse { error ->
            Log.e(TAG, "Unable to read Shizuku state", error)
            ShizukuState.Failure(ShizukuFailureReason.STATE_READ_FAILED)
        }
    }

    fun requestPermission() {
        if (!Shizuku.pingBinder()) {
            refresh()
            return
        }

        runCatching {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        }.onFailure { error ->
            Log.e(TAG, "Unable to request Shizuku permission", error)
            mutableState.value =
                ShizukuState.Failure(ShizukuFailureReason.PERMISSION_REQUEST_FAILED)
        }
    }

    private fun readReadyState(): ShizukuState =
        ShizukuState.Ready(
            serverVersion = Shizuku.getVersion(),
            serverUid = Shizuku.getUid(),
        )

    @Suppress("DEPRECATION")
    private fun isShizukuInstalled(): Boolean =
        runCatching {
            packageManager.getApplicationInfo(SHIZUKU_PACKAGE_NAME, 0)
        }.isSuccess

    private companion object {
        const val TAG = "ShizukuController"
        const val PERMISSION_REQUEST_CODE = 100
        const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
    }
}
