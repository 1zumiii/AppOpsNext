package dev.izumi.appopsnext.monitor

/** The monitor's configuration after a template was applied, and what it took. */
data class MonitorTemplateApplication(
    val targets: List<MonitorTarget>,
    val points: List<MonitorPointSettings>,
    val packageNames: List<String>,
    /** The template's operations that were set up, in the template's order. */
    val appliedOperations: List<String>,
    /** Operations this device's table does not know, which could not be watched. */
    val skippedOperations: List<String>,
)

/**
 * Gives a template's monitoring points to a set of apps.
 *
 * Applying only adds: each app keeps every operation it already watched and
 * gains the template's. The settings of the points the template names are a
 * different matter, since a template is a statement about how those points
 * should be reported, so they are overwritten with the rule's values. Every
 * other app and every other point is left as it was.
 */
object MonitorTemplateApplier {
    /**
     * @param isAvailable whether this device can watch an operation. One it cannot
     *   is skipped rather than failing the whole template, because a template is
     *   kept across devices and backups.
     */
    fun apply(
        template: MonitorTemplate,
        packageNames: Collection<String>,
        targets: List<MonitorTarget>,
        points: List<MonitorPointSettings>,
        isAvailable: (String) -> Boolean,
    ): MonitorTemplateApplication {
        val rules = template.rules.distinctBy(MonitorTemplateRule::operationName)
        val (usable, unavailable) = rules.partition { isAvailable(it.operationName) }
        val packages = packageNames.filter(String::isNotEmpty).distinct()
        val operations = usable.map(MonitorTemplateRule::operationName)
        val skipped = unavailable.map(MonitorTemplateRule::operationName)
        if (packages.isEmpty() || usable.isEmpty()) {
            return MonitorTemplateApplication(targets, points, packages, operations, skipped)
        }

        val existing = targets.associateBy(MonitorTarget::packageName)
        val updatedTargets = targets.map { target ->
            if (target.packageName in packages) {
                target.copy(operationNames = target.operationNames + operations)
            } else {
                target
            }
        } + packages.filterNot(existing::containsKey).map { MonitorTarget(it, operations.toSet()) }

        val touched = packages.flatMap { packageName -> operations.map { packageName to it } }.toSet()
        val updatedPoints = points.filterNot { (it.packageName to it.operationName) in touched } +
            packages.flatMap { packageName -> usable.map { it.settingsFor(packageName) } }
                // A rule with every setting at its default is stored as no entry at all.
                .filterNot(MonitorPointSettings::isDefault)

        return MonitorTemplateApplication(
            targets = updatedTargets.sortedBy(MonitorTarget::packageName),
            points = updatedPoints.sortedWith(
                compareBy(MonitorPointSettings::packageName, MonitorPointSettings::operationName),
            ),
            packageNames = packages,
            appliedOperations = operations,
            skippedOperations = skipped,
        )
    }
}
