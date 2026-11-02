package io.github.krosstack.kscope.analyzer.gradle.tooling

import io.github.krosstack.kscope.gradle.model.KscopeModel
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.gradle.GradleBuild
import java.io.Serializable

/**
 * Runs inside the Gradle daemon: asks every project of the build, and of the builds it includes, for its model and
 * returns the documents as one JSON array. Everything is fetched by one action, so the build is configured once.
 */
internal class FetchProjectModelsAction :
    BuildAction<String>,
    Serializable {
    override fun execute(controller: BuildController): String =
        documentsOf(controller.buildModel, controller).joinToString(",", "[", "]")

    private fun documentsOf(build: GradleBuild, controller: BuildController): List<String> =
        build.projects.map { controller.getModel(it, KscopeModel::class.java).json } +
            build.includedBuilds.flatMap { documentsOf(it, controller) }

    private companion object {
        private const val serialVersionUID = 1L
    }
}
