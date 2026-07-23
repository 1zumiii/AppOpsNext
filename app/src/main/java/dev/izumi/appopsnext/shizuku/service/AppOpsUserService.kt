package dev.izumi.appopsnext.shizuku.service

import android.content.Context
import android.os.Build
import android.system.Os
import androidx.annotation.Keep
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.command.AppOpsCommands
import dev.izumi.appopsnext.appops.command.CommandExecutor
import dev.izumi.appopsnext.appops.model.ShellCommandResult
import dev.izumi.appopsnext.shizuku.IPrivilegedAppOpsService
import kotlin.system.exitProcess

class AppOpsUserService : IPrivilegedAppOpsService.Stub {
    private val commandExecutor = CommandExecutor()

    constructor() : super()

    @Keep
    constructor(
        @Suppress("UNUSED_PARAMETER") context: Context,
    ) : super()

    override fun getUid(): Int = Os.getuid()

    override fun getPid(): Int = Os.getpid()

    override fun getApiLevel(): Int = Build.VERSION.SDK_INT

    override fun getPackageOps(packageName: String): ShellCommandResult =
        commandExecutor.execute(AppOpsCommands.getPackageOps(packageName))

    override fun getPackageOp(
        packageName: String,
        operationName: String,
    ): ShellCommandResult =
        commandExecutor.execute(
            AppOpsCommands.getPackageOp(packageName, operationName),
        )

    override fun getUidOps(uid: Int): ShellCommandResult =
        commandExecutor.execute(AppOpsCommands.getUidOps(uid))

    override fun setPackageOpMode(
        packageName: String,
        operationName: String,
        mode: String,
    ): ShellCommandResult {
        val validatedMode = requireNotNull(AppOpMode.fromShellValue(mode)) {
            "Unsupported AppOps mode"
        }
        return commandExecutor.execute(
            AppOpsCommands.setPackageOpMode(
                packageName = packageName,
                operationName = operationName,
                mode = validatedMode,
            ),
        )
    }

    override fun setUidOpMode(
        packageName: String,
        operationName: String,
        mode: String,
    ): ShellCommandResult {
        val validatedMode = requireNotNull(AppOpMode.fromShellValue(mode)) {
            "Unsupported AppOps mode"
        }
        return commandExecutor.execute(
            AppOpsCommands.setUidOpMode(
                packageName = packageName,
                operationName = operationName,
                mode = validatedMode,
            ),
        )
    }

    override fun destroy() {
        commandExecutor.close()
        exitProcess(0)
    }
}
