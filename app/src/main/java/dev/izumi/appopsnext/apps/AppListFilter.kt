package dev.izumi.appopsnext.apps

import dev.izumi.appopsnext.apps.model.InstalledApp

object AppListFilter {
    fun apply(
        apps: List<InstalledApp>,
        query: String,
        hideSystemApps: Boolean = false,
    ): List<InstalledApp> {
        val normalizedQuery = query.trim()

        return apps.filter { app ->
            (!hideSystemApps || !app.isSystemApp) &&
                (
                    normalizedQuery.isEmpty() ||
                        app.label.contains(normalizedQuery, ignoreCase = true) ||
                        app.packageName.contains(
                            normalizedQuery,
                            ignoreCase = true,
                        )
                    )
        }
    }
}
