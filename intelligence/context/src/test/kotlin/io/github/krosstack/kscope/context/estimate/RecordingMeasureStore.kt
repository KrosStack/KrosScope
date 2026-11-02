package io.github.krosstack.kscope.context.estimate

import io.github.krosstack.kscope.context.BlockMeasureKey
import io.github.krosstack.kscope.context.TokenCountKey
import io.github.krosstack.kscope.context.TokenCountStore
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.model.context.TokenizerId

internal class RecordingMeasureStore : TokenCountStore {
    private val counts = mutableMapOf<TokenCountKey, TokenCount>()
    val measures = mutableMapOf<BlockMeasureKey, Long>()
    val recalls = mutableListOf<BlockMeasureKey>()
    val offers = mutableListOf<BlockMeasureKey>()
    var hits = 0
        private set

    override fun recall(key: TokenCountKey): TokenCount? = counts[key]

    override fun remember(key: TokenCountKey, count: TokenCount) {
        counts[key] = count
    }

    override fun recallMeasure(key: BlockMeasureKey): Long? {
        recalls += key
        return measures[key]?.also { hits++ }
    }

    override fun rememberMeasure(key: BlockMeasureKey, measure: Long) {
        offers += key
        measures[key] = measure
    }

    /** Measures that are wrong on purpose, kept under keys that differ from the ones that were offered. */
    fun withOtherKeysOnly(): RecordingMeasureStore = RecordingMeasureStore().also { other ->
        for ((key, measure) in measures) {
            val wrong = measure + WRONG_BY
            other.measures[key.copy(layoutDigest = zeros())] = wrong
            other.measures[key.copy(contentHash = ContentHash(zeros()))] = wrong
            other.measures[key.copy(estimator = TokenizerId("another"))] = wrong
            other.measures[key.copy(textDigest = zeros())] = wrong
            key.range?.let { range -> other.measures[key.copy(range = range.shifted())] = wrong }
        }
    }

    /** The measures of the blocks that come from the graph only, as they were offered. */
    fun ofGraphBlocksOnly(): RecordingMeasureStore = keeping { it.textDigest != null }

    /** The measures of the blocks that are slices of a file only, as they were offered. */
    fun ofFileBlocksOnly(): RecordingMeasureStore = keeping { it.textDigest == null }

    /** The measures as they were offered, those of the blocks that come from the graph each raised by [by]. */
    fun withGraphBlocksRaised(by: Long): RecordingMeasureStore = RecordingMeasureStore().also { other ->
        for ((key, measure) in measures) other.measures[key] = if (key.textDigest != null) measure + by else measure
    }

    private fun keeping(wanted: (BlockMeasureKey) -> Boolean): RecordingMeasureStore =
        RecordingMeasureStore().also { other -> other.measures += measures.filterKeys(wanted) }

    private fun zeros() = "0".repeat(DIGEST_LENGTH)

    private fun TextRange.shifted() = copy(startLine = startLine + FAR_AWAY)

    private companion object {
        const val WRONG_BY = 1_000L
        const val FAR_AWAY = 1_000_000
        const val DIGEST_LENGTH = 64
    }
}
