package io.github.krosstack.kscope.spi.embedding

import io.github.krosstack.kscope.spi.search.SearchScope

public interface VectorIndex {
    /** Hits ordered by score, highest first, then by node key. */
    public fun nearest(query: FloatArray, scope: SearchScope, limit: Int): List<VectorHit>
}
