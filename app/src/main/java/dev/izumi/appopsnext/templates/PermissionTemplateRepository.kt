package dev.izumi.appopsnext.templates

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpNames
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.templates.model.PermissionTemplate
import dev.izumi.appopsnext.templates.model.PermissionTemplateRule
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.permissionTemplatesDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "permission_templates")

class PermissionTemplateRepository(
    context: Context,
) {
    private val dataStore = context.permissionTemplatesDataStore

    val templates: Flow<List<PermissionTemplate>> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences ->
            PermissionTemplateCodec.decode(preferences[Keys.TEMPLATES])
        }

    suspend fun create(name: String): String {
        val template = PermissionTemplate(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            rules = PermissionTemplateDefaults.commonRules,
        )
        updateTemplates { templates -> templates + template }
        return template.id
    }

    suspend fun rename(templateId: String, name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        updateTemplate(templateId) { template ->
            template.copy(name = trimmedName)
        }
    }

    suspend fun updateRule(
        templateId: String,
        stableOperationName: String,
        mode: AppOpMode,
        scope: AppOpScope,
    ) {
        val normalizedName = AppOpNames.stableName(stableOperationName)
        updateTemplate(templateId) { template ->
            val updatedRule = PermissionTemplateRule(
                stableOperationName = normalizedName,
                mode = mode,
                scope = scope,
            )
            template.copy(
                rules = template.rules
                    .filterNot {
                        it.stableOperationName == normalizedName
                    } + updatedRule,
            )
        }
    }

    suspend fun removeRule(
        templateId: String,
        stableOperationName: String,
    ) {
        val normalizedName = AppOpNames.stableName(stableOperationName)
        updateTemplate(templateId) { template ->
            template.copy(
                rules = template.rules.filterNot {
                    it.stableOperationName == normalizedName
                },
            )
        }
    }

    suspend fun delete(templateId: String) {
        updateTemplates { templates ->
            templates.filterNot { it.id == templateId }
        }
    }

    private suspend fun updateTemplate(
        templateId: String,
        transform: (PermissionTemplate) -> PermissionTemplate,
    ) {
        updateTemplates { templates ->
            templates.map { template ->
                if (template.id == templateId) {
                    transform(template)
                } else {
                    template
                }
            }
        }
    }

    private suspend fun updateTemplates(
        transform: (List<PermissionTemplate>) -> List<PermissionTemplate>,
    ) {
        dataStore.edit { preferences ->
            val currentTemplates =
                PermissionTemplateCodec.decode(preferences[Keys.TEMPLATES])
            preferences[Keys.TEMPLATES] = PermissionTemplateCodec.encode(
                transform(currentTemplates),
            )
        }
    }

    private object Keys {
        val TEMPLATES = stringPreferencesKey("templates_v1")
    }
}
