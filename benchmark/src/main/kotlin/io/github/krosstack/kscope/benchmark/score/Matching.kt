package io.github.krosstack.kscope.benchmark.score

/**
 * One element of a comparison: [key] decides whether two elements are the same, [label] is what a report shows and
 * [group] splits the counts of a report, for example by edge kind.
 */
internal class Item(val key: String, val label: String, val group: String = "")

/** The outcome of matching expected elements against produced ones, element for element. */
internal class Matching(val pairs: List<Pair<Item, Item>>, val missed: List<Item>, val extra: List<Item>) {
    val matched: List<Item> get() = pairs.map { it.first }

    fun score(): Score = Score.of(pairs.size, missed.size, extra.size, missed.labels(), extra.labels())

    private fun List<Item>.labels() = map { it.label }.sorted()

    companion object {
        /** Equal keys match pairwise, so an element expected twice must be produced twice. */
        fun of(expected: List<Item>, produced: List<Item>): Matching {
            val producedByKey = produced.groupBy { it.key }
            val expectedByKey = expected.groupBy { it.key }
            val pairs = expectedByKey.flatMap { (key, items) -> items.zip(producedByKey[key].orEmpty()) }
            val missed = expectedByKey.flatMap { (key, items) -> items.drop(producedByKey[key].orEmpty().size) }
            val extra = producedByKey.flatMap { (key, items) -> items.drop(expectedByKey[key].orEmpty().size) }
            return Matching(pairs, missed, extra)
        }
    }
}
