package dev.izumi.appopsnext.guard

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.izumi.appopsnext.AppOpsNextApplication

/**
 * The hourly check of [GuardMode.PERIODIC], and the check when a delayed restore
 * falls due. A check that cannot read is retried on the next run.
 */
class GuardWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val trigger = inputData.getString(KEY_TRIGGER) ?: "periodic"
        (applicationContext as AppOpsNextApplication).guardController.runWorkerCheck(trigger)
        return Result.success()
    }

    companion object {
        const val KEY_TRIGGER = "trigger"
    }
}
