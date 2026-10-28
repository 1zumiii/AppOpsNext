package dev.izumi.appopsnext.apps.model

data class InstalledApp(
    val label: String,
    val packageName: String,
    val uid: Int,
    val isSystemApp: Boolean,
    /** Null for the user this app runs in. */
    val userId: Int? = null,
)
