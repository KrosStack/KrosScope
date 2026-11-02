package io.github.krosstack.kscope.retrieval

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.spi.search.SearchHit
import io.github.krosstack.kscope.spi.search.SearchIndex
import io.github.krosstack.kscope.spi.search.SearchQuery
import io.github.krosstack.kscope.spi.search.SearchScope

internal class RetrievalIndex(
    private val vocabulary: Set<String> = emptySet(),
    private val hits: List<SearchHit> = emptyList(),
) : SearchIndex {
    override fun search(query: SearchQuery): List<SearchHit> = hits.filter {
        (it.node is NodeRef.OfArtifact) == (query.scope == SearchScope.ARTIFACTS)
    }.take(query.limit)
    override fun vocabularyContains(term: String, scope: SearchScope) = term in vocabulary
    override fun vocabularyWithPrefix(prefix: String, scope: SearchScope, limit: Int) =
        vocabulary.filter { it.startsWith(prefix) }.sorted().take(limit)
    override fun documentFrequency(term: String, scope: SearchScope) = if (term in vocabulary) 1 else 0
}
