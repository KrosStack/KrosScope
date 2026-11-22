package io.github.krosstack.kscope.retrieval

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.retrieval.analysis.QueryAnalyzer
import io.github.krosstack.kscope.spi.search.SearchHit
import io.github.krosstack.kscope.spi.search.SearchIndex
import io.github.krosstack.kscope.spi.search.SearchQuery
import io.github.krosstack.kscope.spi.search.SearchScope

internal class Seeds(val hits: List<SearchHit>, val capped: Boolean)

internal class SeedSearch(
    private val graph: RetrievalGraph,
    private val index: SearchIndex,
    private val mostCandidates: Int = MOST_CANDIDATES,
) {
    fun find(query: AnalyzedQuery, scope: SearchScope, limit: Int, within: RepositoryScope): Seeds {
        val candidates = if (scope == SearchScope.ARTIFACTS) ARTIFACT_CANDIDATES else limit
        val asked = SearchQuery(QueryAnalyzer.searchTerms(query), scope, candidates)
        if (!within.narrowed) return Seeds(usable(index.search(asked), limit), capped = false)
        return narrowed(asked, limit, within)
    }

    private tailrec fun narrowed(asked: SearchQuery, limit: Int, within: RepositoryScope): Seeds {
        val matches = index.search(asked)
        val seeds = usable(within.hits(matches), limit)
        val more = matches.size >= asked.limit
        return when {
            seeds.size >= limit || !more -> Seeds(seeds, capped = false)
            asked.limit >= mostCandidates -> Seeds(seeds, capped = true)
            else -> narrowed(asked.copy(limit = minOf(asked.limit * GROWTH, mostCandidates)), limit, within)
        }
    }

    private fun usable(matches: List<SearchHit>, limit: Int): List<SearchHit> =
        matches.filter { hit -> usableSeed(hit.node) }.take(limit)

    private fun usableSeed(node: NodeRef): Boolean {
        val artifact = (node as? NodeRef.OfArtifact)?.let { graph.artifacts[it.ref] }
        val generated = artifact?.generated
        return generated == null || generated.sourceOfTruth != null
    }

    companion object {
        const val MOST_CANDIDATES = 50_000
        private const val ARTIFACT_CANDIDATES = 100
        private const val GROWTH = 4
    }
}
