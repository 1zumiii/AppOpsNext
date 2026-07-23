package dev.izumi.appopsnext.appops.model

sealed interface PackageOpsLoadResult {
    data class Success(
        val snapshot: PackageOpsSnapshot,
    ) : PackageOpsLoadResult

    data class Failure(
        val reason: AppOpsReadFailureReason,
    ) : PackageOpsLoadResult
}
