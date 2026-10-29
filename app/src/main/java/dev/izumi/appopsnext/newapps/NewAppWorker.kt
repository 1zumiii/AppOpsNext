package dev.izumi.appopsnext.newapps

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.izumi.appopsnext.AppOpsNextApplication

/**
 * Looks for new apps every 15 minutes while automatic rules are on. The live
 * package receiver only works while the process runs, so this catches the
 * installs made while it did not.
 */
class NewAppWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        (applicationContext as AppOpsNextApplication).newAppPolicyCoordinator.runPeriodicCheck()
        return Result.success()
    }
}
