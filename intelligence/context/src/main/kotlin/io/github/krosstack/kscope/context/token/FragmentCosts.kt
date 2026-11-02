package io.github.krosstack.kscope.context.token

import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.context.ContextRendering
import io.github.krosstack.kscope.model.context.TokenCount

/** What the renderer and the merge of fragments need to fill the token count of a fragment they build. */
internal interface FragmentCosts {
    fun count(hash: ContentHash, range: TextRange?, rendering: ContextRendering, text: String): TokenCount
}

/**
 * Counts nothing. A fragment built with it carries a count of zero and serves only the estimate of a package, which
 * reads no fragment count and never returns a fragment.
 */
internal object UncountedFragments : FragmentCosts {
    override fun count(hash: ContentHash, range: TextRange?, rendering: ContextRendering, text: String): TokenCount =
        TokenCount(0)
}
