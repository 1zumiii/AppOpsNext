package dev.izumi.appopsnext.appops.model

data class AppOpEntry(
    val name: String,
    val mode: String,
    val details: String?,
    val hasUidModePrefix: Boolean,
)
data class PackageOpsSnapshot(
    val packageName: String,
    val entries: List<AppOpEntry>,
    val rawOutput: String,
)
