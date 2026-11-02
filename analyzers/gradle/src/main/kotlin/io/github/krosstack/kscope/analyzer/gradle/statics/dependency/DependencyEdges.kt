package io.github.krosstack.kscope.analyzer.gradle.statics.dependency

import io.github.krosstack.kscope.analyzer.gradle.mapping.ConfigurationScopes
import io.github.krosstack.kscope.analyzer.gradle.mapping.TreeAttributes
import io.github.krosstack.kscope.analyzer.gradle.statics.ScriptDiagnostics
import io.github.krosstack.kscope.analyzer.gradle.statics.StaticDiagnostics
import io.github.krosstack.kscope.model.BuildRootId
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.SourceSet

/** What one dependency declaration contributes: dependency edges, references to catalog entries, diagnostics. */
internal class DeclaredEdges(
    val dependencies: List<Relationship>,
    val references: List<Relationship>,
    val problems: List<Diagnostic>,
) {
    companion object {
        val none = DeclaredEdges(emptyList(), emptyList(), emptyList())
    }
}

/**
 * Turns dependency declarations of one module into `DEPENDS_ON` relationships: module to module for project
 * dependencies, module to the external coordinate `maven:group:name` for libraries, with the configuration and the
 * version as attributes, and module to `file:<path in the repository>` for each file of `files(...)`, or
 * `file:<directory>/` for a `fileTree(...)`, with the path as written. A declaration in a convention plugin is
 * heuristic.
 */
internal class DependencyEdges(
    private val module: ModuleId,
    private val buildRoot: BuildRootId,
    private val projects: Set<String>,
    private val sourceSets: List<SourceSet>,
    private val anchors: FileAnchors,
) {
    fun of(declaration: DependencyDeclaration, notation: Notation): DeclaredEdges = when (notation) {
        is Notation.Project -> DeclaredEdges(listOf(projectEdge(declaration, notation.path)), emptyList(), emptyList())
        is Notation.Library -> libraryEdges(declaration, notation)
        is Notation.Bundle -> bundleEdges(declaration, notation)
        is Notation.Files -> DeclaredEdges(fileEdges(declaration, notation.files), emptyList(), emptyList())
        is Notation.MissingAlias -> missingAlias(declaration, notation)
        is Notation.Unknown -> unknown(declaration, notation.expression)
        Notation.Ignored -> DeclaredEdges.none
    }

    private fun libraryEdges(declaration: DependencyDeclaration, library: Notation.Library): DeclaredEdges {
        val references = library.reference?.let {
            listOf(CatalogReferences.relationship(declaration.source, declaration.call, it, library.heuristic))
        }.orEmpty()
        return DeclaredEdges(listOf(externalEdge(declaration, library)), references, emptyList())
    }

    private fun bundleEdges(declaration: DependencyDeclaration, bundle: Notation.Bundle): DeclaredEdges {
        val reference =
            CatalogReferences.relationship(declaration.source, declaration.call, bundle.reference, bundle.heuristic)
        return DeclaredEdges(bundle.members.map { externalEdge(declaration, it) }, listOf(reference), emptyList())
    }

    private fun missingAlias(declaration: DependencyDeclaration, notation: Notation.MissingAlias): DeclaredEdges {
        val location = declaration.source.location(declaration.call)
        val problem = ScriptDiagnostics.aliasUnresolved(notation.missing.catalog, notation.missing.accessor, location)
        return DeclaredEdges(emptyList(), emptyList(), listOf(problem))
    }

    private fun unknown(declaration: DependencyDeclaration, expression: String): DeclaredEdges {
        val problem = ScriptDiagnostics.dependencyNotAnalysed(expression, declaration.source.location(declaration.call))
        return DeclaredEdges(emptyList(), emptyList(), listOf(problem))
    }

    private fun projectEdge(declaration: DependencyDeclaration, path: String): Relationship {
        val certainty = CatalogReferences.certainty(declaration.source.heuristic)
        val resolution = if (path in projects) {
            Resolution.Resolved(NodeRef.OfModule(ModuleId(buildRoot.value + path)), certainty)
        } else {
            Resolution.Unresolved(path, ReasonCode(StaticDiagnostics.PROJECT_NOT_FOUND))
        }
        return edge(declaration, resolution, emptyMap())
    }

    private fun externalEdge(declaration: DependencyDeclaration, library: Notation.Library): Relationship {
        val certainty = CatalogReferences.certainty(declaration.source.heuristic || library.heuristic)
        val resolution = Resolution.Resolved(NodeRef.OfExternal("maven:${library.module}"), certainty)
        return edge(declaration, resolution, listOfNotNull(library.version?.let { "version" to it }).toMap())
    }

    private fun fileEdges(declaration: DependencyDeclaration, files: List<DeclaredFile>): List<Relationship> {
        val certainty = CatalogReferences.certainty(declaration.source.heuristic)
        return files.mapNotNull { file ->
            anchors.pathOf(file)?.let { path ->
                val target = NodeRef.OfExternal(FILE_PREFIX + path + if (file.tree) "/" else "")
                val written = mapOf("path" to file.path) + TreeAttributes.of(file.include, file.exclude)
                edge(declaration, Resolution.Resolved(target, certainty), written)
            }
        }
    }

    private fun edge(declaration: DependencyDeclaration, resolution: Resolution, extra: Map<String, String>) =
        Relationship(
            from = NodeRef.OfModule(module),
            kind = RelationKind.DEPENDS_ON,
            subKind = null,
            resolution = resolution,
            evidenceClass = EvidenceClass.BUILD_MODEL,
            site = SourceLocation(declaration.source.artifact.ref, declaration.call.range),
            scope = Scope(sourceSet = scopeOf(declaration)),
            attributes = mapOf("configuration" to declaration.configuration) + extra,
        )

    private fun scopeOf(declaration: DependencyDeclaration) =
        declaration.sourceSet?.let { name -> sourceSets.firstOrNull { it.name == name }?.id }
            ?: ConfigurationScopes.sourceSetOf(declaration.configuration, sourceSets)

    private companion object {
        const val FILE_PREFIX = "file:"
        const val SEPARATOR = "\n"
    }
}
