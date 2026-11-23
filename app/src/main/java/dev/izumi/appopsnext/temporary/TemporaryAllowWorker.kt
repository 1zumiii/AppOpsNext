package dev.izumi.appopsnext.temporary

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.izumi.appopsnext.AppOpsNextApplication

internal class TemporaryAllowWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result =
        if ((applicationContext as AppOpsNextApplication).temporaryAllowController.check()) Result.retry()
        else Result.success()
}
