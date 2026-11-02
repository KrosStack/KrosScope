package io.github.krosstack.kscope.engine.build

import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Repository
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.spi.BuildModel

/**
 * Resolves the dependencies on `maven:group:artifact` that a module declares to the modules of other repositories
 * that publish that coordinate. Coordinates are never matched inside the consumer's own repository, where project
 * dependencies apply, and a coordinate that several modules publish is not linked. A module that publishes the
 * coordinate in several versions counts once, with the version the consumer pins when it has one.
 */
internal class CoordinateResolution(repositories: List<Repository>, private val model: BuildModel) {
    private val repositoryOfRoot = model.buildRoots.associate { it.id to it.repository }
    private val modules = model.modules.associateBy { it.id }
    private val moduleOfSourceSet = model.sourceSets.associate { it.id to it.module }
    private val directories = repositories.associate { it.id to it.directory.value.ifEmpty { it.name } }
    private val index = PublicationIndex(model.modules, model.targets)
    private val edges = CoordinateEdges(::nameOf)

    fun resolve(): CoordinateLinks {
        val outcomes = model.dependencies.mapNotNull(::declarationOf).flatMap(::outcomesOf)
        val links = outcomes.mapNotNull { it.link }.distinct().sortedBy { encoded(it) }
        val diagnostics = outcomes.mapNotNull { it.diagnostic }.distinct().sortedBy { encoded(it) }
        return CoordinateLinks(links, diagnostics)
    }

    private fun declarationOf(edge: Relationship): Declaration? {
        val target = (edge.resolution as? Resolution.Resolved)?.target as? NodeRef.OfExternal
        val parts = target?.coordinate?.takeIf { it.startsWith(MAVEN) }?.removePrefix(MAVEN)?.split(':').orEmpty()
        val consumer = moduleOf(edge.from)
        return if (edge.kind == RelationKind.DEPENDS_ON && consumer != null && parts.size >= 2) {
            Declaration(edge, consumer, parts[0], parts[1])
        } else {
            null
        }
    }

    private fun outcomesOf(declaration: Declaration): List<Outcome> {
        val producers = index.find(declaration.group, declaration.artifact)
            .filter { repositoryOf(it.module) != repositoryOf(declaration.consumer) }
            .groupBy { it.module.id }.values
            .map { publicationFor(declaration, it) }
        return when {
            producers.isEmpty() -> emptyList()
            producers.size > 1 -> listOf(ambiguous(declaration, producers))
            else -> listOf(edges.link(declaration, producers.single()))
        }
    }

    private fun publicationFor(declaration: Declaration, publications: List<Publication>): Publication =
        publications.firstOrNull { it.version == declaration.pinned } ?: publications.first()

    private fun ambiguous(declaration: Declaration, producers: List<Publication>): Outcome {
        val names = producers.map { nameOf(it.module) }.sorted()
        val finding = Finding(nameOf(declaration.consumer), declaration.coordinate, declaration.edge.site)
        return Outcome(null, CoordinateTexts.ambiguous(finding, names))
    }

    private fun moduleOf(node: NodeRef): Module? = when (node) {
        is NodeRef.OfModule -> modules[node.id]
        is NodeRef.OfSourceSet -> moduleOfSourceSet[node.id]?.let(modules::get)
        is NodeRef.OfSymbol, is NodeRef.OfArtifact, is NodeRef.OfExternal -> null
    }

    private fun repositoryOf(module: Module): RepositoryId? = repositoryOfRoot[module.buildRoot]

    private fun nameOf(module: Module): String = "${directories[repositoryOf(module)].orEmpty()} (${module.path})"

    private fun encoded(relationship: Relationship): String =
        KscopeJson.encodeToString(Relationship.serializer(), relationship)

    private fun encoded(diagnostic: Diagnostic): String = KscopeJson.encodeToString(Diagnostic.serializer(), diagnostic)

    private companion object {
        const val MAVEN = "maven:"
    }
}
