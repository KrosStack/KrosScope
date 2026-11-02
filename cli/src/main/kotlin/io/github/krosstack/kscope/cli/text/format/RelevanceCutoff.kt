package io.github.krosstack.kscope.cli.text.format

/**
 * The one rule that decides which ranked items a text result calls relevant. An item is relevant when its score is at
 * least [SHARE_OF_BEST] of the best score, which is the best score among the items that are not [pinned]; a pinned
 * item is the node that was asked about, and it always counts. The best [MINIMUM] items are relevant whatever their
 * scores, so a list is never empty and never a single line.
 */
internal object RelevanceCutoff {
    const val SHARE_OF_BEST = 0.30
    const val MINIMUM = 3

    fun <T> select(items: List<T>, pinned: (T) -> Boolean = { false }, score: (T) -> Double): Selection<T> {
        val best = items.filterNot(pinned).maxOfOrNull(score) ?: 0.0
        val relevant = items.withIndex().filter { (index, item) ->
            pinned(item) || index < MINIMUM || score(item) >= SHARE_OF_BEST * best
        }.map { it.value }
        return Selection(relevant, items.size)
    }

    fun percent(): String = "${Math.round(SHARE_OF_BEST * PERCENT)}%"

    private const val PERCENT = 100
}

/** The [relevant] items of a ranked list, best first, and how many items were [scored] in all. */
internal class Selection<T>(val relevant: List<T>, val scored: Int) {
    val belowCutoff: Int get() = scored - relevant.size
}
