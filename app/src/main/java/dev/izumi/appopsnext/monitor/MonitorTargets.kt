package dev.izumi.appopsnext.monitor

import dev.izumi.appopsnext.appops.model.AppOpNames

/** The operations watched for one package. */
data class MonitorTarget(
    val packageName: String,
    val operationNames: Set<String>,
)

/**
 * Stores targets one package per line, as `package op,op,op`.
 *
 * Nothing is watched until the user picks something, so an absent or unreadable
 * value decodes to an empty selection rather than to a default set. Fields an
 * earlier build wrote to carry reporting settings are ignored here: how a
 * watched operation is reported lives in [MonitorThrottlesCodec], which keeps a
 * setting after its operation has been unpicked.
 */
object MonitorTargetsCodec {
    fun decode(value: String?): List<MonitorTarget> {
        if (value.isNullOrBlank()) return emptyList()
        return value
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .mapNotNull { line ->
                val fields = line.split(' ').filter(String::isNotEmpty)
                val packageName = fields.firstOrNull().orEmpty()
                if (packageName.isEmpty()) return@mapNotNull null
                val operations = fields
                    .getOrNull(1)
                    .orEmpty()
                    .split(',')
                    .map { it.substringBefore('@').trim() }
                    .filter(String::isNotEmpty)
                    .map(AppOpNames::stableName)
                    .toSet()
                if (operations.isEmpty()) null else MonitorTarget(packageName, operations)
            }
            .distinctBy(MonitorTarget::packageName)
            .toList()
    }

    fun encode(targets: List<MonitorTarget>): String =
        targets
            .filter { it.operationNames.isNotEmpty() }
            .joinToString(separator = "\n") { target ->
                "${target.packageName} ${target.operationNames.sorted().joinToString(",")}"
            }
}
