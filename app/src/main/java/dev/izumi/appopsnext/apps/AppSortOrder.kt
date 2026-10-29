package dev.izumi.appopsnext.apps

import dev.izumi.appopsnext.apps.model.InstalledApp

enum class AppSortOrder {
    NAME,

    /** Newest first. */
    INSTALL_TIME,

    /** Newest first. */
    UPDATE_TIME,
    ;

    /**
     * [apps] come sorted by name, and the sort is stable, so apps with the same
     * time, or with no time known, stay in name order. Apps without a time go last.
     */
    fun sort(apps: List<InstalledApp>): List<InstalledApp> {
        val time: (InstalledApp) -> Long? = when (this) {
            NAME -> return apps
            INSTALL_TIME -> InstalledApp::firstInstallTimeMillis
            UPDATE_TIME -> InstalledApp::lastUpdateTimeMillis
        }
        return apps.sortedWith(compareByDescending(nullsFirst(), time))
    }

    companion object {
        fun fromName(name: String?): AppSortOrder? = entries.firstOrNull { it.name == name }
    }
}
