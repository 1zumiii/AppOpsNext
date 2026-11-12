package dev.izumi.appopsnext.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import dev.izumi.appopsnext.diagnostics.DiagnosticLogRepository
import dev.izumi.appopsnext.diagnostics.DiagnosticLogTier
import dev.izumi.appopsnext.shizuku.model.ShizukuState
import dev.izumi.appopsnext.shizuku.model.ShizukuFailureReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

class ShizukuController(
    context: Context,
    private val diagnosticLog: DiagnosticLogRepository,
) {
    private val packageManager = context.packageManager

    private val mutableState = MutableStateFlow<ShizukuState>(ShizukuState.Checking)
    val state: StateFlow<ShizukuState> = mutableState.asStateFlow()

    private var started = false

    /** Set until the first state is read, which the start line then reports. */
    private var announceStart = false

    // A binder that arrives changes the state, which refresh() writes. The sticky
    // registration in start() also calls this for a binder that was already
    // there, which is no news beyond the start line.
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refresh()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        diagnosticLog.warning(LOG_SOURCE, "Shizuku binder died.")
        mutableState.value = ShizukuState.Unavailable(isShizukuInstalled())
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != PERMISSION_REQUEST_CODE) return@OnRequestPermissionResultListener

            mutableState.value = if (grantResult == PackageManager.PERMISSION_GRANTED) {
                diagnosticLog.info(LOG_SOURCE, "Shizuku permission granted.", DiagnosticLogTier.IMPORTANT)
                readReadyState()
            } else {
                diagnosticLog.warning(LOG_SOURCE, "Shizuku permission denied.")
                ShizukuState.PermissionDenied
            }
        }

    fun start() {
        if (started) return
        started = true
        announceStart = true
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
        val previousState = mutableState.value
        val nextState = runCatching {
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
            diagnosticLog.error(
                source = LOG_SOURCE,
                message = "Unable to read Shizuku state.",
                error = error,
            )
            ShizukuState.Failure(ShizukuFailureReason.STATE_READ_FAILED)
        }
        mutableState.value = nextState
        if (announceStart) {
            announceStart = false
            diagnosticLog.info(
                source = LOG_SOURCE,
                message = "Shizuku controller started. state=$nextState",
                tier = DiagnosticLogTier.IMPORTANT,
            )
        } else if (previousState != nextState) {
            diagnosticLog.info(
                source = LOG_SOURCE,
                message = "State changed: $previousState -> $nextState",
                tier = DiagnosticLogTier.IMPORTANT,
            )
        }
    }

    fun requestPermission() {
        if (!Shizuku.pingBinder()) {
            diagnosticLog.warning(
                LOG_SOURCE,
                "Permission request skipped because binder is unavailable.",
            )
            refresh()
            return
        }

        diagnosticLog.info(LOG_SOURCE, "Requesting Shizuku permission.")
        runCatching {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        }.onFailure { error ->
            Log.e(TAG, "Unable to request Shizuku permission", error)
            diagnosticLog.error(
                source = LOG_SOURCE,
                message = "Unable to request Shizuku permission.",
                error = error,
            )
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
        const val LOG_SOURCE = "Shizuku"
        const val PERMISSION_REQUEST_CODE = 100
        const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
    }
}
