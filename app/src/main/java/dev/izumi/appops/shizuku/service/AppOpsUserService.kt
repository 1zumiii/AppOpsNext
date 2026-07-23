package dev.izumi.appops.shizuku.service

import android.content.Context
import android.os.Build
import android.system.Os
import androidx.annotation.Keep
import dev.izumi.appops.appops.command.AppOpMode
import dev.izumi.appops.appops.command.AppOpsCommands
import dev.izumi.appops.appops.command.CommandExecutor
import dev.izumi.appops.appops.model.ShellCommandResult
import dev.izumi.appops.shizuku.IPrivilegedAppOpsService
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

    override fun destroy() {
        commandExecutor.close()
        exitProcess(0)
    }
}
