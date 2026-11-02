package io.github.krosstack.kscope.benchmark.workspace

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.SymbolOrigin

/**
 * A line of code that uses a generated symbol, and the spec symbol in another repository that the symbol is generated
 * from. The link starts at the line of the use and carries the evidence class of the generation.
 */
internal class GeneratedSurfaceLinks : LinkProducer {
    override fun produce(graph: WorkspaceGraph): List<ProducedLink> =
        graph.relationships.flatMap { use -> linksOf(graph, use) }.distinct().filter { it.crossesRepositories }

    private fun linksOf(graph: WorkspaceGraph, use: Relationship): List<ProducedLink> {
        val site = use.site
        val generated = graph.symbol(graph.target(use))?.takeIf { it.origin is SymbolOrigin.Generated }
        if (site == null || generated == null) return emptyList()
        val generations = graph.reader.incoming(NodeRef.OfSymbol(generated.id), setOf(RelationKind.GENERATES))
        val from = LinkEnd.At(graph.placeOf(site))
        return generations.mapNotNull { generation ->
            val spec = graph.at(graph.symbol(generation.from))
            spec?.let { ProducedLink(from, it, generation.evidenceClass.wireName()) }
        }
    }
}
