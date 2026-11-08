package dev.izumi.appopsnext.apps

import dev.izumi.appopsnext.apps.model.InstalledApp

enum class AppSortOrder {
    NAME,

    /** Newest first. */
    INSTALL_TIME,

    /** Newest first. */
    UPDATE_TIME,

    /**
     * A filter rather than an order: only the apps this app changed, the latest
     * change first.
     */
    CHANGED_ONLY,
    ;

    /**
     * [apps] come sorted by name, and the sort is stable, so apps with the same
     * time, or with no time known, stay in name order. Apps without a time go last.
     * [changedTime] gives the time of this app's last change, where there is one.
     */
    fun sort(
        apps: List<InstalledApp>,
        changedTime: (InstalledApp) -> Long? = { null },
    ): List<InstalledApp> {
        val time: (InstalledApp) -> Long? = when (this) {
            NAME -> return apps
            INSTALL_TIME -> InstalledApp::firstInstallTimeMillis
            UPDATE_TIME -> InstalledApp::lastUpdateTimeMillis
            CHANGED_ONLY -> return apps
                .mapNotNull { app -> changedTime(app)?.let { app to it } }
                .sortedByDescending { it.second }
                .map { it.first }
        }
        return apps.sortedWith(compareByDescending(nullsFirst(), time))
    }

    companion object {
        fun fromName(name: String?): AppSortOrder? = entries.firstOrNull { it.name == name }
    }
}
