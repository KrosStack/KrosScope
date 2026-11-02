package io.github.krosstack.kscope.service.graph

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.retrieval.ImpactResult
import io.github.krosstack.kscope.retrieval.TaskRetrieval
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.search.SearchIndex

/**
 * Answers an impact question from the rows around the nodes it reaches. The analysis itself is the one that reads the
 * whole index; it is run on the loaded rows, and run again with the rows of every node it newly reached, until a run
 * reaches nothing that was not loaded. That run saw every row it could have asked for, so its answer is the answer of
 * the whole index. A question that reaches more than [LIMIT] nodes gives up with null, and the caller reads the index.
 */
internal class ImpactClosure(
    private val graph: GraphReader,
    private val search: SearchIndex,
    private val limit: Int = LIMIT,
) {
    fun analyze(text: String): ImpactResult? = settle(text, LoadedFacts(graph, limit), roots(text))

    private tailrec fun settle(text: String, facts: LoadedFacts, pending: Set<NodeRef>): ImpactResult? {
        if (!facts.load(pending)) return null
        val result = TaskRetrieval(facts.materialize(), search).impact(text)
        val next = (result.roots + result.affected.map { it.node }).toSet() - facts.loaded
        return if (next.isEmpty()) result else settle(text, facts, next)
    }

    private fun roots(text: String): Set<NodeRef> {
        val lookups = PointLookups(graph)
        val artifacts = lookups.artifactsAt(text).map { it.ref }
        val modules = graph.structure().modules.filter { it.id.value == text || it.path == text }
        return buildSet {
            artifacts.forEach { add(NodeRef.OfArtifact(it)) }
            artifacts.flatMap(lookups::declaredIn).forEach { add(NodeRef.OfSymbol(it.id)) }
            lookups.symbolsMatching(text).forEach { add(NodeRef.OfSymbol(it.id)) }
            modules.forEach { add(NodeRef.OfModule(it.id)) }
        }
    }

    companion object {
        const val LIMIT = 2_000
    }
}
