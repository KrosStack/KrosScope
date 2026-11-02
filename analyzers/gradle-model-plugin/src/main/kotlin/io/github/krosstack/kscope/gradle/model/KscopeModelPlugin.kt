package io.github.krosstack.kscope.gradle.model

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.tooling.provider.model.ToolingModelBuilder
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import javax.inject.Inject

/**
 * Applied to every project of a build by an init script. It gathers what the Android plugin only offers while the
 * project is configured and makes the project's model available to the Tooling API.
 */
class KscopeModelPlugin @Inject constructor(private val registry: ToolingModelBuilderRegistry) : Plugin<Project> {
    override fun apply(project: Project) {
        AndroidVariantHook.install(project, KscopeProjectState.install(project))
        registry.register(KscopeModelBuilder())
    }
}

class KscopeModelBuilder : ToolingModelBuilder {
    override fun canBuild(modelName: String): Boolean = modelName == KscopeModel::class.java.name

    override fun buildAll(modelName: String, project: Project): Any {
        val document = ProjectDocument(project, KscopeProjectState.of(project)).build()
        return DefaultKscopeModel(JsonWriter.write(document))
    }
}
