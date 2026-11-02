package io.github.krosstack.kscope.context.token

/**
 * An estimator whose count of a concatenation follows from one measure per block, as long as the blocks are
 * [BpeBlockBoundary.standalone] and valid Unicode.
 */
internal interface BlockAdditive {
    fun measure(block: String): Long

    fun tokens(totalMeasure: Long): Int
}
