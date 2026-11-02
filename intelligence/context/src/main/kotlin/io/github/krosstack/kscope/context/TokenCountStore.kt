package io.github.krosstack.kscope.context

import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.context.ContextRendering
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.model.context.TokenizerId

/**
 * What identifies the token count of one rendered text: the content hash of the file it comes from, the source range
 * and rendering, the estimator, and a digest of the text itself, because a signature or an outline is not a function
 * of the file alone. A whole rendered package has no file and no rendering: its [contentHash] is the digest of its
 * text and [rendering] is null.
 */
public data class TokenCountKey(
    public val contentHash: ContentHash,
    public val range: TextRange?,
    public val rendering: ContextRendering?,
    public val estimator: TokenizerId,
    public val textDigest: String,
)

/**
 * What identifies the measure of one fragment block without building the block: the content hash of the file, the
 * range and rendering of the fragment, the estimator, and [layoutDigest], a digest of what else the block holds and the
 * file does not: the path as printed, the evidence note and the layout version of this module.
 *
 * A block whose text is the slice of a file that the range and the rendering name has no [textDigest]. The text of any
 * other block, a signature, an outline or a generated summary, is a function of the graph and not of the file, so its
 * key holds a digest of that text too; its [range] is null unless the fragment has one. Keys with and without a
 * [textDigest] are never equal.
 */
public data class BlockMeasureKey(
    public val contentHash: ContentHash,
    public val range: TextRange?,
    public val rendering: ContextRendering,
    public val estimator: TokenizerId,
    public val layoutDigest: String,
    public val textDigest: String? = null,
) {
    init {
        require(range != null || textDigest != null) { "A block measure key names a range or a text" }
    }
}

/**
 * Keeps token counts between runs. A count that is recalled must equal the count the estimator would give, so a store
 * that cannot vouch for an entry answers null. The compiler counts the text itself on a miss and offers the result.
 */
public interface TokenCountStore {
    public fun recall(key: TokenCountKey): TokenCount?

    public fun remember(key: TokenCountKey, count: TokenCount)

    /**
     * Recalls the measure of a block, which is not a token count: for a character estimator it is a number of code
     * points, and the count is taken once over the sum of the measures of all blocks. Only the measure of a block that
     * is standalone is ever offered, so a recalled measure may be added to the others without counting the block.
     */
    public fun recallMeasure(key: BlockMeasureKey): Long? = null

    public fun rememberMeasure(key: BlockMeasureKey, measure: Long): Unit = Unit

    public companion object {
        /** Keeps nothing. */
        public val NONE: TokenCountStore = object : TokenCountStore {
            override fun recall(key: TokenCountKey): TokenCount? = null

            override fun remember(key: TokenCountKey, count: TokenCount) = Unit
        }
    }
}
