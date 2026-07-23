package dev.izumi.appopsnext.templates

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpNames
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.templates.model.PermissionTemplate
import dev.izumi.appopsnext.templates.model.PermissionTemplateRule
import java.nio.charset.StandardCharsets
import java.util.Base64

internal object PermissionTemplateCodec {
    private const val HEADER = "APP_OPS_NEXT_TEMPLATES_V1"
    private const val TEMPLATE_RECORD = "T"
    private const val RULE_RECORD = "R"
    private const val FIELD_SEPARATOR = "\t"

    fun encode(templates: List<PermissionTemplate>): String =
        buildString {
            appendLine(HEADER)
            templates.forEach { template ->
                appendLine(
                    listOf(
                        TEMPLATE_RECORD,
                        encodeField(template.id),
                        encodeField(template.name),
                    ).joinToString(FIELD_SEPARATOR),
                )
                template.rules.forEach { rule ->
                    appendLine(
                        listOf(
                            RULE_RECORD,
                            encodeField(template.id),
                            encodeField(rule.stableOperationName),
                            rule.mode.name,
                            rule.scope.name,
                        ).joinToString(FIELD_SEPARATOR),
                    )
                }
            }
        }

    fun decode(rawValue: String?): List<PermissionTemplate> {
        if (rawValue.isNullOrBlank()) return emptyList()
        val lines = rawValue.lineSequence().toList()
        if (lines.firstOrNull() != HEADER) return emptyList()

        val namesById = linkedMapOf<String, String>()
        val rulesById =
            linkedMapOf<String, MutableList<PermissionTemplateRule>>()

        lines.drop(1).forEach { line ->
            val fields = line.split(FIELD_SEPARATOR)
            when (fields.firstOrNull()) {
                TEMPLATE_RECORD -> {
                    if (fields.size != 3) return@forEach
                    val id = decodeField(fields[1]) ?: return@forEach
                    val name = decodeField(fields[2]) ?: return@forEach
                    if (id.isBlank() || name.isBlank()) return@forEach
                    namesById[id] = name
                    rulesById.getOrPut(id, ::mutableListOf)
                }

                RULE_RECORD -> {
                    if (fields.size != 5) return@forEach
                    val templateId = decodeField(fields[1])
                        ?: return@forEach
                    val operationName = decodeField(fields[2])
                        ?: return@forEach
                    val mode = runCatching {
                        AppOpMode.valueOf(fields[3])
                    }.getOrNull() ?: return@forEach
                    val scope = runCatching {
                        AppOpScope.valueOf(fields[4])
                    }.getOrNull() ?: return@forEach
                    rulesById.getOrPut(templateId, ::mutableListOf).add(
                        PermissionTemplateRule(
                            stableOperationName =
                                AppOpNames.stableName(operationName),
                            mode = mode,
                            scope = scope,
                        ),
                    )
                }
            }
        }

        return namesById.map { (id, name) ->
            PermissionTemplate(
                id = id,
                name = name,
                rules = rulesById[id]
                    .orEmpty()
                    .distinctBy(PermissionTemplateRule::stableOperationName),
            )
        }
    }

    private fun encodeField(value: String): String =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeField(value: String): String? =
        runCatching {
            String(
                Base64.getUrlDecoder().decode(value),
                StandardCharsets.UTF_8,
            )
        }.getOrNull()
}
