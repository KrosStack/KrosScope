package io.github.krosstack.kscope.analyzer.gradle.mapping

import io.github.krosstack.kscope.analyzer.gradle.ProjectDocument
import io.github.krosstack.kscope.analyzer.gradle.workspace.WorkspacePaths
import io.github.krosstack.kscope.model.BuildRootId
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId
import java.nio.file.Path

/**
 * Turns the declared dependencies of a project into `DEPENDS_ON` relationships: module to module for project
 * dependencies, module to external coordinate for libraries, and module to `file:<path in the repository>` for each
 * file of a file dependency, with its path from the project, and to `file:<directory>/` for a file tree, which carries
 * its patterns and leaves its files to be read when the edge is used. A dependency declared in a configuration that
 * belongs to one source set, such as `commonMainApi`, is scoped to that source set.
 */
internal class DependencyMapper(private val knownModules: Set<ModuleId>, private val workspace: WorkspacePaths) {
    fun edges(
        document: ProjectDocument,
        buildRoot: BuildRootId,
        module: ModuleId,
        sourceSets: List<SourceSet>,
    ): List<Relationship> {
        val projects = document.dependencies.projects.mapNotNull { dependency ->
            val target = ModuleId(buildRoot.value + dependency.path)
            if (target in knownModules) {
                edge(module, NodeRef.OfModule(target), dependency.configuration, emptyMap(), sourceSets)
            } else {
                null
            }
        }
        val external = document.dependencies.external.map { dependency ->
            val coordinate = NodeRef.OfExternal("maven:${dependency.coordinates}")
            val version = listOfNotNull(dependency.version?.let { "version" to it }).toMap()
            edge(module, coordinate, dependency.configuration, version, sourceSets)
        }
        val files = document.dependencies.files.mapNotNull { dependency ->
            workspace.reach(dependency.path)?.let { path ->
                val written = mapOf("path" to fromProject(document.dir, dependency.path))
                edge(module, NodeRef.OfExternal("file:$path"), dependency.configuration, written, sourceSets)
            }
        }
        val trees = document.dependencies.trees.mapNotNull { tree ->
            workspace.reach(tree.dir)?.takeIf { it.isNotEmpty() }?.let { path ->
                val written = mapOf("path" to fromProject(document.dir, tree.dir)) +
                    TreeAttributes.of(tree.include, tree.exclude)
                edge(module, NodeRef.OfExternal("file:$path/"), tree.configuration, written, sourceSets)
            }
        }
        return (projects + external + files + trees).distinct()
    }

    private fun fromProject(projectDirectory: String, file: String): String =
        Path.of(projectDirectory).relativize(Path.of(file)).toString().replace('\\', '/')

    private fun edge(
        from: ModuleId,
        target: NodeRef,
        configuration: String,
        extra: Map<String, String>,
        sourceSets: List<SourceSet>,
    ) = Relationship(
        from = NodeRef.OfModule(from),
        kind = RelationKind.DEPENDS_ON,
        subKind = null,
        resolution = Resolution.Resolved(target, Certainty.EXACT),
        evidenceClass = EvidenceClass.BUILD_MODEL,
        site = null,
        scope = Scope(sourceSet = sourceSetOf(configuration, sourceSets)),
        attributes = mapOf("configuration" to configuration) + extra,
    )

    private fun sourceSetOf(configuration: String, sourceSets: List<SourceSet>): SourceSetId? =
        ConfigurationScopes.sourceSetOf(configuration, sourceSets)
}
