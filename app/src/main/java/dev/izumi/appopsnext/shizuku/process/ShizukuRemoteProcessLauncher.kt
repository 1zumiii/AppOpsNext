package dev.izumi.appopsnext.shizuku.process

import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku

/**
 * Isolates the deprecated Shizuku remote-process protocol used only to
 * bootstrap the native compatibility backend.
 */
class ShizukuRemoteProcessLauncher {
    fun launch(
        arguments: List<String>,
        environment: Map<String, String> = emptyMap(),
        workingDirectory: String? = null,
    ): RemoteProcessHandle {
        require(arguments.isNotEmpty()) {
            "A remote process requires at least one argument"
        }
        check(Shizuku.pingBinder()) {
            "Shizuku Binder is unavailable"
        }

        val service = IShizukuService.Stub.asInterface(
            requireNotNull(Shizuku.getBinder()) {
                "Shizuku Binder is unavailable"
            },
        )
        val remoteProcess = service.newProcess(
            arguments.toTypedArray(),
            environment
                .map { (name, value) -> "$name=$value" }
                .toTypedArray()
                .takeIf { it.isNotEmpty() },
            workingDirectory,
        )
        return RemoteProcessHandle(remoteProcess)
    }
}
