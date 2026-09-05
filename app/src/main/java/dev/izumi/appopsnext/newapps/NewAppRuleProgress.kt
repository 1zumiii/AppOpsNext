package dev.izumi.appopsnext.newapps

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.*
import dev.izumi.appopsnext.batch.model.BatchOperationItemResult
import dev.izumi.appopsnext.batch.model.BatchOperationTarget
import dev.izumi.appopsnext.newapps.model.InstalledPackageFingerprint
import java.util.Base64

/** A durable outcome for one rule, keyed by installation and requested policy. */
data class NewAppRuleProgress(
    val installation: InstalledPackageFingerprint,
    val item: BatchOperationItemResult,
) {
    val retryable: Boolean get() = isRetryable(item.result)

    fun matches(target: BatchOperationTarget): Boolean =
        item.target.stableOperationName == target.stableOperationName &&
            item.target.requestedMode == target.requestedMode &&
            item.target.preferredScope == target.preferredScope

    companion object {
        fun isRetryable(result: AppOpModeChangeResult): Boolean =
            result is AppOpModeChangeResult.Failure &&
                result.phase == AppOpModeChangePhase.READ_ORIGINAL &&
                result.originalMode == null &&
                result.restorationStatus == AppOpsRestorationStatus.NOT_REQUIRED
    }
}

internal object NewAppRuleProgressCodec {
    fun encode(record: NewAppRuleProgress): String {
        val target = record.item.target
        val success = record.item.result as? AppOpModeChangeResult.Success
        val failure = record.item.result as? AppOpModeChangeResult.Failure
        return listOf(
            "1", record.installation.firstInstallTimeMillis.toString(), target.packageName,
            target.appLabel, target.uid.toString(), target.stableOperationName,
            target.preferredScope.name, target.requestedMode.name,
            if (success != null) "success" else "failure",
            (success?.originalMode ?: failure?.originalMode)?.name.orEmpty(),
            (success?.appliedMode ?: failure?.observedMode)?.name.orEmpty(),
            failure?.phase?.name.orEmpty(), failure?.restorationStatus?.name.orEmpty(),
        ).joinToString("|") { Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8)) }
    }

    fun decode(value: String): NewAppRuleProgress? = runCatching {
        val f = value.split('|').map { String(Base64.getDecoder().decode(it), Charsets.UTF_8) }
        require(f.size == 13 && f[0] == "1")
        val installation = InstalledPackageFingerprint(f[2], f[1].toLong())
        val target = BatchOperationTarget(f[2], f[3], f[4].toInt(), f[5], AppOpScope.valueOf(f[6]), AppOpMode.valueOf(f[7]))
        val result = when (f[8]) {
            "success" -> AppOpModeChangeResult.Success(AppOpMode.valueOf(f[9]), AppOpMode.valueOf(f[10]))
            "failure" -> AppOpModeChangeResult.Failure(
                AppOpModeChangePhase.valueOf(f[11]),
                f[9].takeIf(String::isNotEmpty)?.let(AppOpMode::valueOf),
                f[10].takeIf(String::isNotEmpty)?.let(AppOpMode::valueOf),
                AppOpsRestorationStatus.valueOf(f[12]),
            )
            else -> error("Unknown outcome")
        }
        NewAppRuleProgress(installation, BatchOperationItemResult(target, result))
    }.getOrNull()
}
