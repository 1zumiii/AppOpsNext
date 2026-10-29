package dev.izumi.appopsnext.newapps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager

/**
 * Woken by [NewAppWatcherService] when an install finishes, even while the app
 * process is not running. Only a sender holding INTERACT_ACROSS_USERS_FULL, such
 * as the shell, can reach it. The work runs in an expedited job rather than here,
 * because applying rules can take longer than a receiver may run.
 */
class NewAppInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_FINISHED) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            // An install during a run gets a run of its own after it.
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<NewAppWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build(),
        )
    }

    companion object {
        const val ACTION_INSTALL_FINISHED = "dev.izumi.appopsnext.action.INSTALL_FINISHED"
        private const val WORK_NAME = "new_app_install_check"
    }
}
