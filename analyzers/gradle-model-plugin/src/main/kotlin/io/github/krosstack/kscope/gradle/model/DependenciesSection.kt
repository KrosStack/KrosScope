package io.github.krosstack.kscope.gradle.model

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.ConfigurableFileTree

/**
 * The dependencies declared in the build script, per configuration. Resolution results are not read here; the files
 * of a file dependency are listed as they are, which runs no task. A file tree is not listed by the files it holds
 * when the model is fetched but by its directory and patterns, so that its files are read again whenever the
 * model is used.
 */
internal object DependenciesSection {
    // implementation, commonMainApi, debugCompileOnly: the configurations a build script declares into
    private val dependencyScopes = listOf("api", "implementation", "compileOnly", "compileOnlyApi", "runtimeOnly")

    fun describe(project: Project): Map<String, Any?> {
        val declared = project.configurations.filter(::isDeclarable)
        val projects = declared.flatMap { configuration -> projectEdges(project, configuration) }
        val external = declared.flatMap { configuration -> externalEdges(configuration) }
        val files = declared.flatMap { configuration -> fileEdges(configuration) }
        val trees = declared.flatMap { configuration -> treeEdges(configuration) }
        return linkedMapOf(
            "projects" to projects.distinct().sortedWith(compareBy({ it["configuration"] }, { it["path"] })),
            "external" to external.distinct().sortedWith(compareBy({ it["configuration"] }, { it["coordinates"] })),
            "files" to files.distinct().sortedWith(compareBy({ it["configuration"] }, { it["path"] })),
            "trees" to trees.distinct().sortedWith(compareBy({ "${it["configuration"]}" }, { "${it["dir"]}" })),
        )
    }

    private fun isDeclarable(configuration: Configuration): Boolean {
        val declarable = Reflect.get(configuration, "canBeDeclared") as? Boolean ?: true
        return declarable && isScriptDeclarationScope(configuration.name)
    }

    private fun isScriptDeclarationScope(name: String): Boolean = dependencyScopes.any { scope ->
        name == scope || name.endsWith(scope.replaceFirstChar { it.uppercase() })
    }

    private fun projectEdges(project: Project, configuration: Configuration) =
        configuration.dependencies.filterIsInstance<ProjectDependency>().mapNotNull { dependency ->
            projectPath(dependency)?.takeIf { it != project.path }?.let { projectEdge(configuration, it) }
        }

    private fun externalEdges(configuration: Configuration) =
        configuration.dependencies.filterIsInstance<ExternalModuleDependency>().map { externalEdge(configuration, it) }

    private fun fileEdges(configuration: Configuration) =
        configuration.dependencies.filterIsInstance<FileCollectionDependency>()
            .filter { it.files !is ConfigurableFileTree }
            .flatMap { dependency ->
                dependency.files.files.sortedBy { it.path }.map { file ->
                    linkedMapOf("configuration" to configuration.name, "path" to Values.slashPath(file))
                }
            }

    private fun treeEdges(configuration: Configuration) =
        configuration.dependencies.filterIsInstance<FileCollectionDependency>()
            .mapNotNull { it.files as? ConfigurableFileTree }
            .map { tree ->
                linkedMapOf(
                    "configuration" to configuration.name,
                    "dir" to Values.slashPath(tree.dir),
                    "include" to tree.includes.sorted(),
                    "exclude" to tree.excludes.sorted(),
                )
            }

    private fun projectPath(dependency: Dependency): String? {
        val path = Reflect.get(dependency, "path") ?: Reflect.get(Reflect.get(dependency, "dependencyProject"), "path")
        return path?.toString()
    }

    private fun projectEdge(configuration: Configuration, path: String) =
        linkedMapOf("configuration" to configuration.name, "path" to path)

    private fun externalEdge(configuration: Configuration, dependency: ExternalModuleDependency) = linkedMapOf(
        "configuration" to configuration.name,
        "coordinates" to "${dependency.group}:${dependency.name}",
        "version" to dependency.version,
    )
}
