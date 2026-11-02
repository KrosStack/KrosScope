package io.github.krosstack.kscope.benchmark.workspace

import io.github.krosstack.kscope.model.RelationKind

/**
 * A symbol that is linked to another symbol by a relationship of the given kind and sub-kind. The links count when
 * they lie between two repositories, or [withinRepository] when the class is one whose links stay in a repository.
 */
internal class SymbolLinks(
    private val kind: RelationKind,
    private val subKind: String,
    private val withinRepository: Boolean = false,
) : LinkProducer {
    override fun produce(graph: WorkspaceGraph): List<ProducedLink> =
        graph.relationships(kind, subKind).mapNotNull { relationship ->
            val from = graph.at(graph.symbol(relationship.from))
            val to = graph.at(graph.symbol(graph.target(relationship)))
            if (from == null || to == null) null else ProducedLink(from, to, relationship.evidenceClass.wireName())
        }.filter { withinRepository || it.crossesRepositories }
}
