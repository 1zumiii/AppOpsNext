package dev.izumi.appopsnext.newapps.model

data class InstalledPackageFingerprint(
    val packageName: String,
    val firstInstallTimeMillis: Long,
)

data class InstalledPackageRecord(
    val fingerprint: InstalledPackageFingerprint,
    val label: String,
    val uid: Int,
)

data class NewAppReconcileResult(
    val initializedBaseline: Boolean,
    val detected: List<InstalledPackageFingerprint>,
)
