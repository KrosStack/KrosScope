package io.github.krosstack.kscope.retrieval

import io.github.krosstack.kscope.spi.search.SearchHit
import io.github.krosstack.kscope.spi.search.SearchIndex
import io.github.krosstack.kscope.spi.search.SearchQuery
import io.github.krosstack.kscope.spi.search.SearchScope

internal class RecordingIndex(private val inner: SearchIndex) : SearchIndex by inner {
    val queries = mutableListOf<SearchQuery>()

    override fun search(query: SearchQuery): List<SearchHit> {
        queries += query
        return inner.search(query)
    }

    fun limits(scope: SearchScope): List<Int> = queries.filter { it.scope == scope }.map { it.limit }
}
