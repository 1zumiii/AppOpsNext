package dev.izumi.appopsnext.monitor

import dev.izumi.appopsnext.appops.model.AppOpNames
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * How a monitor template sets up one operation: the same four settings a
 * monitoring point has, without the package, which the template is applied to.
 */
data class MonitorTemplateRule(
    val operationName: String,
    /** The shortest gap between two reports, or null to report every access. */
    val throttleSeconds: Int? = null,
    /** Null follows the monitor's own notification setting. */
    val headsUp: Boolean? = null,
    val outcomes: MonitorOutcomes = MonitorOutcomes.ALL,
    val backgroundOnly: Boolean = false,
) {
    /** The point this rule sets up for [packageName]. */
    fun settingsFor(packageName: String): MonitorPointSettings =
        MonitorPointSettings(
            packageName = packageName,
            operationName = operationName,
            throttleSeconds = throttleSeconds,
            headsUp = headsUp,
            outcomes = outcomes,
            backgroundOnly = backgroundOnly,
        )

    companion object {
        fun from(settings: MonitorPointSettings): MonitorTemplateRule =
            MonitorTemplateRule(
                operationName = settings.operationName,
                throttleSeconds = settings.throttleSeconds,
                headsUp = settings.headsUp,
                outcomes = settings.outcomes,
                backgroundOnly = settings.backgroundOnly,
            )
    }
}

/**
 * A named set of monitoring points that can be given to several apps at once.
 *
 * This is not a permission template: it does not change what an app may do, only
 * what the monitor watches and how it reports it.
 */
data class MonitorTemplate(
    val id: String,
    val name: String,
    /** In the order the user arranged them, one per operation. */
    val rules: List<MonitorTemplateRule>,
) {
    val operationNames: List<String> get() = rules.map(MonitorTemplateRule::operationName)

    /**
     * Keeps the rules whose operation is still in [names], in their order and
     * with their settings, and adds a default rule for each new operation after
     * them, so picking permissions never resets one that was already set up.
     */
    fun withOperations(names: Collection<String>): MonitorTemplate {
        val wanted = names.map(AppOpNames::stableName).toSet()
        val kept = rules.filter { it.operationName in wanted }
        val keptNames = kept.map(MonitorTemplateRule::operationName).toSet()
        val added = wanted.filterNot(keptNames::contains).sorted().map(::MonitorTemplateRule)
        return copy(rules = kept + added)
    }

    fun withRule(
        operationName: String,
        change: (MonitorTemplateRule) -> MonitorTemplateRule,
    ): MonitorTemplate =
        copy(rules = rules.map { if (it.operationName == operationName) change(it) else it })
}

/**
 * Stores custom templates as a block of lines each:
 *
 * ```
 * template <id> <name>
 * rule <operation> <field> <field>...
 * ```
 *
 * A rule belongs to the template line above it, and its fields are the ones a
 * monitoring point is stored with, so a setting added to points later reads the
 * same way here. Names are typed by the user and may hold spaces, line breaks or
 * anything else, so the name is URL-encoded into one field. A line that cannot be
 * read is dropped on its own; a template line that cannot be read takes its rules
 * with it, so they cannot end up in the template above.
 *
 * Built-in templates are part of the app, not of the stored list, so one is
 * never written and an id that claims to be one is dropped when read.
 */
object MonitorTemplatesCodec {
    private const val TEMPLATE = "template"
    private const val RULE = "rule"
    private const val CHARSET = "UTF-8"

    fun decode(value: String?): List<MonitorTemplate> {
        if (value.isNullOrBlank()) return emptyList()
        val templates = mutableListOf<MonitorTemplate>()
        var current: MonitorTemplate? = null
        fun close() {
            current?.let { templates += it.copy(rules = it.rules.distinctBy(MonitorTemplateRule::operationName)) }
            current = null
        }
        value.lineSequence()
            .map { line -> line.trim().split(' ').filter(String::isNotEmpty) }
            .filter(List<String>::isNotEmpty)
            .forEach { fields ->
                when (fields[0]) {
                    TEMPLATE -> {
                        close()
                        current = decodeHeader(fields)
                    }
                    RULE -> {
                        val rule = decodeRule(fields) ?: return@forEach
                        current = current?.let { it.copy(rules = it.rules + rule) }
                    }
                    // A kind of line a later build may add is passed over.
                    else -> Unit
                }
            }
        close()
        return templates
            .filterNot { BuiltInMonitorTemplates.isBuiltIn(it.id) }
            .distinctBy(MonitorTemplate::id)
    }

    private fun decodeHeader(fields: List<String>): MonitorTemplate? {
        if (fields.size < 3) return null
        val name = decodeName(fields[2]).trim()
        if (name.isEmpty()) return null
        return MonitorTemplate(id = fields[1], name = name, rules = emptyList())
    }

    private fun decodeRule(fields: List<String>): MonitorTemplateRule? {
        val raw = fields.getOrNull(1) ?: return null
        if (AppOpNames.shellName(raw).isEmpty()) return null
        val operationName = AppOpNames.stableName(raw)
        val settings = MonitorPointSettingsCodec.readFields(
            MonitorPointSettings(packageName = "", operationName = operationName),
            fields.drop(2),
        )
        return MonitorTemplateRule.from(settings)
    }

    /** A name an editor damaged is still better shown as written than dropped. */
    private fun decodeName(encoded: String): String =
        runCatching { URLDecoder.decode(encoded, CHARSET) }.getOrDefault(encoded)

    fun encode(templates: List<MonitorTemplate>): String =
        templates
            .filterNot { BuiltInMonitorTemplates.isBuiltIn(it.id) }
            .filter { it.id.isNotEmpty() && it.id.none(Char::isWhitespace) && it.name.isNotBlank() }
            .flatMap { template ->
                listOf("$TEMPLATE ${template.id} ${URLEncoder.encode(template.name, CHARSET)}") +
                    template.rules.map { rule ->
                        (
                            listOf(RULE, rule.operationName) +
                                MonitorPointSettingsCodec.writeFields(rule.settingsFor(""))
                            ).joinToString(" ")
                    }
            }
            .joinToString(separator = "\n")
}
