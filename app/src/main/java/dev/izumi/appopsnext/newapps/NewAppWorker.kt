package dev.izumi.appopsnext.newapps

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.izumi.appopsnext.AppOpsNextApplication

/**
 * Looks for new apps. It runs every 15 minutes while automatic rules are on,
 * which catches installs the live package receiver missed while the process was
 * not running, and once more whenever [NewAppInstallReceiver] is woken.
 */
class NewAppWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        (applicationContext as AppOpsNextApplication).newAppPolicyCoordinator.runPeriodicCheck()
        return Result.success()
    }
}
