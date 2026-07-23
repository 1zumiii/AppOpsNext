package dev.izumi.appops.shizuku.service

import android.content.Context
import android.os.Build
import android.system.Os
import androidx.annotation.Keep
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

    override fun destroy() {
        commandExecutor.close()
        exitProcess(0)
    }
}
