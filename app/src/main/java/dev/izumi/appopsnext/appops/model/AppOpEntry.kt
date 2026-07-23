package dev.izumi.appopsnext.appops.model

data class AppOpEntry(
    val name: String,
    val mode: String,
    val details: String?,
    val hasUidModePrefix: Boolean,
) {
    val scope: AppOpScope
        get() = if (hasUidModePrefix) AppOpScope.UID else AppOpScope.PACKAGE
}

enum class AppOpScope {
    PACKAGE,
    UID,
}
data class PackageOpsSnapshot(
    val packageName: String,
    val entries: List<AppOpEntry>,
    val rawOutput: String,
)
