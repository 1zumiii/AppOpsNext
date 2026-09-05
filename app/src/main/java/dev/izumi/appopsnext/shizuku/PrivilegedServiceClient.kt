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
import dev.izumi.appopsnext.nativebackend.NativeDaemonBootstrapper
import dev.izumi.appopsnext.nativebackend.NativeDaemonGateway
import dev.izumi.appopsnext.shizuku.model.PrivilegedBackendType
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
    @Volatile
    private var nativeGateway: NativeDaemonGateway? = null
    private var bound = false
    private var connectionTimeoutJob: Job? = null
    private var nativeConnectionJob: Job? = null
    private var connectionGeneration = 0
    private val connectionScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val nativeDaemonBootstrapper =
        NativeDaemonBootstrapper(context)

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
                        backendType = PrivilegedBackendType.USER_SERVICE,
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
        if (
            bound ||
            nativeGateway != null ||
            nativeConnectionJob?.isActive == true ||
            mutableState.value is PrivilegedServiceState.Connecting
        ) {
            diagnosticLog.info(
                source = LOG_SOURCE,
                message =
                    "Connection request skipped. bound=$bound, " +
                        "state=${mutableState.value::class.java.simpleName}",
            )
            return
        }

        mutableState.value = PrivilegedServiceState.Connecting(
            PrivilegedBackendType.NATIVE_DAEMON,
        )
        val generation = ++connectionGeneration
        diagnosticLog.info(
            source = NATIVE_LOG_SOURCE,
            message =
                "Starting native daemon backend. api=${Build.VERSION.SDK_INT}, " +
                    "clientVersion=${BuildConfig.VERSION_CODE}",
        )
        nativeConnectionJob = connectionScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    NativeDaemonGateway(
                        nativeDaemonBootstrapper.launch(),
                    )
                }
            }
            if (generation != connectionGeneration) {
                result.getOrNull()?.close()
                return@launch
            }
            result.onSuccess { gateway ->
                nativeGateway = gateway
                val daemonInfo = gateway.info
                val serviceInfo = PrivilegedServiceInfo(
                    uid = daemonInfo.uid,
                    pid = daemonInfo.pid,
                    apiLevel = Build.VERSION.SDK_INT,
                    backendType = PrivilegedBackendType.NATIVE_DAEMON,
                )
                mutableState.value = PrivilegedServiceState.Connected(
                    serviceInfo,
                )
                Log.i(
                    NATIVE_LOG_SOURCE,
                    "Native daemon connected. " +
                        "protocol=${daemonInfo.protocolVersion}, " +
                        "uid=${daemonInfo.uid}, pid=${daemonInfo.pid}",
                )
                diagnosticLog.info(
                    source = NATIVE_LOG_SOURCE,
                    message =
                        "Native daemon connected. " +
                            "protocol=${daemonInfo.protocolVersion}, " +
                            "uid=${daemonInfo.uid}, pid=${daemonInfo.pid}",
                )
            }.onFailure { error ->
                Log.w(TAG, "Native daemon backend unavailable", error)
                diagnosticLog.warning(
                    source = NATIVE_LOG_SOURCE,
                    message =
                        "Native daemon startup failed; falling back to " +
                            "UserService. ${error::class.java.simpleName}: " +
                            error.message.orEmpty(),
                )
                bindUserService()
            }
        }
    }

    private fun bindUserService() {
        mutableState.value = PrivilegedServiceState.Connecting(
            PrivilegedBackendType.USER_SERVICE,
        )
        diagnosticLog.info(
            source = LOG_SOURCE,
            message =
                "Binding UserService fallback. api=${Build.VERSION.SDK_INT}, " +
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
            source = NATIVE_LOG_SOURCE,
            message = "Manual privileged backend retry requested.",
        )
        disconnect()
        connect()
    }

    fun disconnect() {
        connectionGeneration++
        nativeConnectionJob?.cancel()
        nativeConnectionJob = null
        connectionTimeoutJob?.cancel()
        nativeGateway?.close()
        nativeGateway = null
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
                source = NATIVE_LOG_SOURCE,
                message = "Privileged backend connection state cleared.",
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
            if (
                (mutableState.value as? PrivilegedServiceState.Connecting)
                    ?.backendType == PrivilegedBackendType.USER_SERVICE
            ) {
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
            executeNative { it.getPackageOps(packageName) }
                ?: service?.getPackageOps(packageName)
                ?: throw IllegalStateException("Privileged service is unavailable")
        }

    override suspend fun getPackageOp(
        packageName: String,
        operationName: String,
    ): ShellCommandResult =
        withContext(Dispatchers.IO) {
            executeNative { it.getPackageOp(packageName, operationName) }
                ?: service?.getPackageOp(packageName, operationName)
                ?: throw IllegalStateException("Privileged service is unavailable")
        }

    override suspend fun getUidOps(uid: Int): ShellCommandResult =
        withContext(Dispatchers.IO) {
            executeNative { it.getUidOps(uid) }
                ?: service?.getUidOps(uid)
                ?: throw IllegalStateException("Privileged service is unavailable")
        }

    override suspend fun getHistory(
        operationName: String,
    ): ShellCommandResult =
        withContext(Dispatchers.IO) {
            executeNative { it.getHistory(operationName) }
                ?: service?.getHistory(operationName)
                ?: throw IllegalStateException("Privileged service is unavailable")
        }

    override suspend fun setPackageOpMode(
        packageName: String,
        operationName: String,
        mode: AppOpMode,
    ): ShellCommandResult =
        withContext(Dispatchers.IO) {
            executeNative { it.setPackageOpMode(packageName, operationName, mode) } ?: service?.setPackageOpMode(
                    packageName,
                    operationName,
                    mode.shellValue,
                )
                ?: throw IllegalStateException("Privileged service is unavailable")
        }

    override suspend fun setUidOpMode(
        packageName: String,
        operationName: String,
        mode: AppOpMode,
    ): ShellCommandResult =
        withContext(Dispatchers.IO) {
            executeNative { it.setUidOpMode(packageName, operationName, mode) } ?: service?.setUidOpMode(
                    packageName,
                    operationName,
                    mode.shellValue,
                )
                ?: throw IllegalStateException("Privileged service is unavailable")
        }

    private suspend fun executeNative(
        action: suspend (NativeDaemonGateway) -> ShellCommandResult,
    ): ShellCommandResult? {
        val gateway = nativeGateway ?: return null
        try {
            return action(gateway)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            gateway.close()
            connectionScope.launch {
                // An old command must never disconnect a newly established backend.
                if (nativeGateway === gateway) {
                    nativeGateway = null
                    mutableState.value = PrivilegedServiceState.Failure(
                        PrivilegedServiceFailureReason.INITIALIZATION_FAILED,
                    )
                    diagnosticLog.warning(
                        source = NATIVE_LOG_SOURCE,
                        message = "Native command connection lost: ${error.message}",
                    )
                }
            }
            throw error
        }
    }

    private companion object {
        const val TAG = "PrivilegedService"
        const val LOG_SOURCE = "UserService"
        const val NATIVE_LOG_SOURCE = "NativeBackend"
        const val USER_SERVICE_PROCESS_SUFFIX = "appops"
        const val CONNECTION_TIMEOUT_MILLIS = 12_000L
    }
}
