package dev.izumi.appopsnext.presentation.history

import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.history.model.AppOpHistoryEvent

object HistoryEventResolver {
    fun resolve(
        events: List<AppOpHistoryEvent>,
        installedApps: List<InstalledApp>,
        hideSystemApps: Boolean,
    ): List<ResolvedHistoryEvent> {
        val appsByPackage = installedApps.associateBy(
            InstalledApp::packageName,
        )
        return events.mapNotNull { event ->
            val app = appsByPackage[event.packageName]
                ?: return@mapNotNull null
            if (app.uid != event.uid) {
                return@mapNotNull null
            }
            if (hideSystemApps && app.isSystemApp) {
                return@mapNotNull null
            }
            ResolvedHistoryEvent(
                event = event,
                app = app,
            )
        }
    }
}
