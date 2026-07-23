package dev.izumi.appopsnext.apps

import dev.izumi.appopsnext.apps.model.InstalledApp

object AppListFilter {
    fun apply(
        apps: List<InstalledApp>,
        query: String,
    ): List<InstalledApp> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return apps

        return apps.filter { app ->
            app.label.contains(normalizedQuery, ignoreCase = true) ||
                app.packageName.contains(normalizedQuery, ignoreCase = true)
        }
    }
}
