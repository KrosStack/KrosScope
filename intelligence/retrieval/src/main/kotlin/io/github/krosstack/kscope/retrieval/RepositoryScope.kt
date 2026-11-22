package io.github.krosstack.kscope.retrieval

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.spi.search.SearchHit

internal class RepositoryScope(private val graph: RetrievalGraph, private val repository: RepositoryId?) {
    val narrowed: Boolean get() = repository != null

    fun holds(node: NodeRef): Boolean = repository == null || graph.declaredFile(node)?.repository == repository

    fun hits(hits: List<SearchHit>): List<SearchHit> = if (repository == null) hits else hits.filter { holds(it.node) }

    fun scores(propagated: Map<NodeRef, Double>): Map<NodeRef, Double> =
        if (repository == null) propagated else propagated.filterKeys(::holds)

    fun symbols(ranked: List<RankedSymbol>): List<RankedSymbol> =
        if (repository == null) ranked else ranked.filter { holds(NodeRef.OfSymbol(it.symbol.id)) }
}
