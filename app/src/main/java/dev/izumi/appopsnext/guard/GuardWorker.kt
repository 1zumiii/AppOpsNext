package dev.izumi.appopsnext.guard

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.izumi.appopsnext.AppOpsNextApplication

/** The hourly check of [GuardMode.PERIODIC]; a check that cannot read is retried on the next run. */
class GuardWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        (applicationContext as AppOpsNextApplication).guardController.runCheckIfEnabled("periodic")
        return Result.success()
    }
}
