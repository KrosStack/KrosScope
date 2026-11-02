package io.github.krosstack.kscope.analyzer.gradle.mapping

import io.github.krosstack.kscope.analyzer.gradle.ProjectDocument
import io.github.krosstack.kscope.analyzer.gradle.workspace.WorkspacePaths
import io.github.krosstack.kscope.model.BuildRoot
import io.github.krosstack.kscope.model.BuildRootId
import io.github.krosstack.kscope.model.BuildSystem
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.spi.BuildModel

internal class MappedModel(
    val model: BuildModel,
    val classpaths: Map<SourceSetId, List<String>>,
    val diagnostics: List<Diagnostic>,
)

/** Maps the project documents of the Gradle model to the build model. Everything it maps is exact build-model data. */
internal class BuildModelMapper(private val repository: RepositoryId, private val workspace: WorkspacePaths) {
    private class Entry(val document: ProjectDocument, val root: BuildRoot, val project: MappedProject)

    fun map(documents: List<ProjectDocument>): MappedModel {
        val builds = documents.groupBy { it.build.rootDir }.toSortedMap()
        val roots = builds.keys.associateWith(::buildRootOf)
        val entries = roots.flatMap { (rootDir, root) ->
            root?.let { entriesOf(it, builds.getValue(rootDir)) }.orEmpty()
        }
        return MappedModel(
            model = modelOf(roots.values.filterNotNull(), entries),
            classpaths = entries.fold(emptyMap()) { all, entry -> all + entry.project.classpaths },
            diagnostics = diagnosticsOf(documents, roots.filterValues { it == null }.keys),
        )
    }

    private fun entriesOf(root: BuildRoot, documents: List<ProjectDocument>): List<Entry> {
        val mapper = ProjectMapper(root, workspace)
        return documents.sortedBy { it.path }.mapNotNull { document ->
            mapper.map(document)?.let { Entry(document, root, it) }
        }
    }

    private fun modelOf(roots: List<BuildRoot>, entries: List<Entry>): BuildModel {
        val dependencies = DependencyMapper(entries.map { it.project.module.id }.toSet(), workspace)
        return BuildModel(
            buildRoots = roots,
            modules = entries.map { it.project.module },
            targets = entries.flatMap { it.project.targets },
            variants = entries.flatMap { it.project.variants },
            sourceSets = entries.flatMap { it.project.sourceSets },
            dependencies = entries.flatMap { entry ->
                dependencies.edges(entry.document, entry.root.id, entry.project.module.id, entry.project.sourceSets)
            },
        )
    }

    private fun buildRootOf(rootDir: String): BuildRoot? {
        val directory = workspace.relative(rootDir) ?: return null
        val id = BuildRootId("${repository.value}//${directory.value}")
        return BuildRoot(id, repository, directory, BuildSystem.GRADLE)
    }

    private fun diagnosticsOf(documents: List<ProjectDocument>, outside: Set<String>): List<Diagnostic> {
        val skipped = outside.map { rootDir ->
            val location = workspace.scrub(rootDir)
            diagnostic(OUTSIDE, "The build in '$location' is outside the workspace and is not modelled.")
        }
        val failed = documents.flatMap { document ->
            document.errors.map { (section, messages) ->
                val first = workspace.scrub(messages.first().lineSequence().first()).take(MAX_MESSAGE)
                val summary = "Section '$section' of project '${document.path}' reported ${messages.size} error(s)"
                diagnostic(SECTION_FAILED, "$summary; first: $first")
            }
        }
        return (skipped + failed).sortedWith(compareBy({ it.code.value }, { it.message }))
    }

    private fun diagnostic(code: String, message: String) =
        Diagnostic(DiagnosticSeverity.WARNING, ReasonCode(code), message, null)

    private companion object {
        const val OUTSIDE = "gradle.tooling.build-outside-workspace"
        const val SECTION_FAILED = "gradle.tooling.section-failed"
        const val MAX_MESSAGE = 300
    }
}
