package dev.izumi.appops.appops

import dev.izumi.appops.appops.model.AppOpsReadFailureReason
import dev.izumi.appops.appops.model.AppOpsReadState
import dev.izumi.appops.appops.parser.PackageOpsParser
import dev.izumi.appops.shizuku.PrivilegedServiceClient

class AppOpsRepository(
    private val privilegedServiceClient: PrivilegedServiceClient,
    private val parser: PackageOpsParser = PackageOpsParser(),
) {
    suspend fun readPackageOps(packageName: String): AppOpsReadState {
        val commandResult = runCatching {
            privilegedServiceClient.getPackageOps(packageName)
        }.getOrElse {
            return AppOpsReadState.Failure(
                AppOpsReadFailureReason.BACKEND_UNAVAILABLE,
            )
        }

        if (commandResult.timedOut) {
            return AppOpsReadState.Failure(
                AppOpsReadFailureReason.COMMAND_TIMED_OUT,
            )
        }
        if (commandResult.exitCode != 0) {
            return AppOpsReadState.Failure(
                AppOpsReadFailureReason.COMMAND_FAILED,
            )
        }

        val snapshot = parser.parse(packageName, commandResult.stdout)
        return AppOpsReadState.Ready(
            operationCount = snapshot.entries.size,
        )
    }
}
