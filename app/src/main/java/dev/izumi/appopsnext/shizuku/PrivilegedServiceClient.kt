package dev.izumi.appopsnext.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import dev.izumi.appopsnext.BuildConfig
import dev.izumi.appopsnext.appops.PrivilegedAppOpsGateway
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.ShellCommandResult
import dev.izumi.appopsnext.diagnostics.DiagnosticLogRepository
import dev.izumi.appopsnext.shizuku.model.PrivilegedServiceInfo
import dev.izumi.appopsnext.shizuku.model.PrivilegedServiceFailureReason
import dev.izumi.appopsnext.shizuku.model.PrivilegedServiceState
import dev.izumi.appopsnext.shizuku.service.AppOpsUserService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class PrivilegedServiceClient(
    context: Context,
    private val diagnosticLog: DiagnosticLogRepository,
) : PrivilegedAppOpsGateway {
    private val mutableState =
        MutableStateFlow<PrivilegedServiceState>(PrivilegedServiceState.Disconnected)
    val state: StateFlow<PrivilegedServiceState> = mutableState.asStateFlow()

    @Volatile
    private var service: IPrivilegedAppOpsService? = null
    private var bound = false
    private var connectionTimeoutJob: Job? = null
    private val connectionScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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
            connectionTimeoutJob?.cancel()
            diagnosticLog.info(
                source = LOG_SOURCE,
                message =
                    "onServiceConnected received; " +
                        "binderPresent=${binder != null}",
            )
            if (binder == null || !binder.pingBinder()) {
                service = null
                diagnosticLog.error(
                    source = LOG_SOURCE,
                    message = "UserService returned an empty or dead binder.",
                )
                mutableState.value = PrivilegedServiceState.Failure(
                    PrivilegedServiceFailureReason.EMPTY_BINDER,
                )
                return
            }

            runCatching {
                IPrivilegedAppOpsService.Stub.asInterface(binder).also { connectedService ->
                    val serviceInfo = PrivilegedServiceInfo(
                        uid = connectedService.uid,
                        pid = connectedService.pid,
                        apiLevel = connectedService.apiLevel,
                    )
                    service = connectedService
                    mutableState.value = PrivilegedServiceState.Connected(
                        serviceInfo,
                    )
                    diagnosticLog.info(
                        source = LOG_SOURCE,
                        message =
                            "UserService connected. uid=${serviceInfo.uid}, " +
                                "pid=${serviceInfo.pid}, " +
                                "api=${serviceInfo.apiLevel}",
                    )
                }
            }.onFailure { error ->
                Log.e(TAG, "Unable to initialize UserService", error)
                diagnosticLog.error(
                    source = LOG_SOURCE,
                    message = "Unable to initialize UserService.",
                    error = error,
                )
                service = null
                mutableState.value = PrivilegedServiceState.Failure(
                    PrivilegedServiceFailureReason.INITIALIZATION_FAILED,
                )
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            connectionTimeoutJob?.cancel()
            service = null
            bound = false
            mutableState.value = PrivilegedServiceState.Disconnected
            diagnosticLog.warning(
                source = LOG_SOURCE,
                message = "UserService disconnected.",
            )
        }
    }

    fun connect() {
        if (bound || mutableState.value is PrivilegedServiceState.Connecting) {
            diagnosticLog.info(
                source = LOG_SOURCE,
                message =
                    "Connection request skipped. bound=$bound, " +
                        "state=${mutableState.value::class.java.simpleName}",
            )
            return
        }

        mutableState.value = PrivilegedServiceState.Connecting
        diagnosticLog.info(
            source = LOG_SOURCE,
            message =
                "Binding UserService. api=${Build.VERSION.SDK_INT}, " +
                    "clientVersion=${BuildConfig.VERSION_CODE}",
        )
        runCatching {
            Shizuku.bindUserService(userServiceArgs, connection)
            bound = true
            diagnosticLog.info(
                source = LOG_SOURCE,
                message =
                    "bindUserService returned; waiting for connection callback.",
            )
            startConnectionTimeout()
        }.onFailure { error ->
            Log.e(TAG, "Unable to bind UserService", error)
            diagnosticLog.error(
                source = LOG_SOURCE,
                message = "bindUserService threw an exception.",
                error = error,
            )
            bound = false
            mutableState.value = PrivilegedServiceState.Failure(
                PrivilegedServiceFailureReason.BIND_FAILED,
            )
        }
    }

    fun retry() {
        diagnosticLog.info(
            source = LOG_SOURCE,
            message = "Manual UserService retry requested.",
        )
        disconnect()
        connect()
    }

    fun disconnect() {
        connectionTimeoutJob?.cancel()
        if (bound) {
            runCatching {
                Shizuku.unbindUserService(userServiceArgs, connection, false)
            }.onFailure { error ->
                Log.w(TAG, "Unable to unbind UserService", error)
                diagnosticLog.warning(
                    source = LOG_SOURCE,
                    message =
                        "Unable to unbind UserService: " +
                            "${error::class.java.simpleName}: " +
                            error.message.orEmpty(),
                )
            }
        }

        if (
            bound ||
            service != null ||
            mutableState.value !is PrivilegedServiceState.Disconnected
        ) {
            diagnosticLog.info(
                source = LOG_SOURCE,
                message = "UserService connection state cleared.",
            )
        }
        service = null
        bound = false
        mutableState.value = PrivilegedServiceState.Disconnected
    }

    private fun startConnectionTimeout() {
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = connectionScope.launch {
            delay(CONNECTION_TIMEOUT_MILLIS)
            if (mutableState.value is PrivilegedServiceState.Connecting) {
                diagnosticLog.error(
                    source = LOG_SOURCE,
                    message =
                        "UserService connection callback timed out after " +
                            "${CONNECTION_TIMEOUT_MILLIS / 1_000}s.",
                )
                mutableState.value = PrivilegedServiceState.Failure(
                    PrivilegedServiceFailureReason.BIND_TIMED_OUT,
                )
            }
        }
    }

    override suspend fun getPackageOps(packageName: String): ShellCommandResult =
        withContext(Dispatchers.IO) {
            val connectedService = service
                ?: throw IllegalStateException("Privileged service is unavailable")
            connectedService.getPackageOps(packageName)
        }

    override suspend fun getPackageOp(
        packageName: String,
        operationName: String,
    ): ShellCommandResult =
        withContext(Dispatchers.IO) {
            val connectedService = service
                ?: throw IllegalStateException("Privileged service is unavailable")
            connectedService.getPackageOp(packageName, operationName)
        }

    override suspend fun getUidOps(uid: Int): ShellCommandResult =
        withContext(Dispatchers.IO) {
            val connectedService = service
                ?: throw IllegalStateException("Privileged service is unavailable")
            connectedService.getUidOps(uid)
        }

    override suspend fun getHistory(
        operationName: String,
    ): ShellCommandResult =
        withContext(Dispatchers.IO) {
            val connectedService = service
                ?: throw IllegalStateException("Privileged service is unavailable")
            connectedService.getHistory(operationName)
        }

    override suspend fun setPackageOpMode(
        packageName: String,
        operationName: String,
        mode: AppOpMode,
    ): ShellCommandResult =
        withContext(Dispatchers.IO) {
            val connectedService = service
                ?: throw IllegalStateException("Privileged service is unavailable")
            connectedService.setPackageOpMode(
                packageName,
                operationName,
                mode.shellValue,
            )
        }

    override suspend fun setUidOpMode(
        packageName: String,
        operationName: String,
        mode: AppOpMode,
    ): ShellCommandResult =
        withContext(Dispatchers.IO) {
            val connectedService = service
                ?: throw IllegalStateException("Privileged service is unavailable")
            connectedService.setUidOpMode(
                packageName,
                operationName,
                mode.shellValue,
            )
        }

    private companion object {
        const val TAG = "PrivilegedService"
        const val LOG_SOURCE = "UserService"
        const val USER_SERVICE_PROCESS_SUFFIX = "appops"
        const val CONNECTION_TIMEOUT_MILLIS = 12_000L
    }
}
