package io.github.krosstack.kscope.benchmark.workspace

import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.Symbol

/**
 * A call that the compiler resolved from a module to a declaration of the module of another repository that it
 * depends on through a dependency of the given sub-kind. The link starts at the line of the call.
 */
internal class CrossRepositoryCalls(private val dependencySubKind: String) : LinkProducer {
    override fun produce(graph: WorkspaceGraph): List<ProducedLink> {
        val dependencies = graph.relationships(RelationKind.DEPENDS_ON, dependencySubKind).mapNotNull { dependency ->
            val from = graph.moduleEnd(dependency.from)
            val to = graph.moduleEnd(graph.target(dependency))
            if (from == null || to == null) null else from to to
        }.toSet()
        val calls = graph.relationships.filter { it.kind == RelationKind.CALLS }
        return calls.filter { it.evidenceClass == EvidenceClass.COMPILER }
            .mapNotNull { call -> linkOf(graph, call, dependencies) }
            .filter { it.crossesRepositories }
    }

    private fun linkOf(
        graph: WorkspaceGraph,
        call: Relationship,
        dependencies: Set<Pair<LinkEnd.OfModule, LinkEnd.OfModule>>,
    ): ProducedLink? {
        val site = call.site ?: return null
        val declaration = graph.symbol(graph.target(call))?.takeIf { dependsOn(graph, site, it, dependencies) }
        val to = graph.at(declaration)
        return to?.let { ProducedLink(LinkEnd.At(graph.placeOf(site)), it, call.evidenceClass.wireName()) }
    }

    private fun dependsOn(
        graph: WorkspaceGraph,
        site: SourceLocation,
        declaration: Symbol,
        dependencies: Set<Pair<LinkEnd.OfModule, LinkEnd.OfModule>>,
    ): Boolean {
        val caller = graph.moduleEndOf(site.artifact)
        val declaring = graph.moduleEndOf(declaration)
        return caller != null && declaring != null && (caller to declaring) in dependencies
    }
}
