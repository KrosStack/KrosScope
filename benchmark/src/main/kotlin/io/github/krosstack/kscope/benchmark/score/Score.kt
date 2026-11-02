package io.github.krosstack.kscope.benchmark.score

import kotlinx.serialization.Serializable

/**
 * Precision, recall and F1 of a set of produced elements against an expected one, with what is missing and what is
 * extra. A side without elements has a ratio of 1, because nothing on it is wrong.
 */
@Serializable
public data class Score(
    public val expected: Int,
    public val produced: Int,
    public val matched: Int,
    public val precision: Double,
    public val recall: Double,
    public val f1: Double,
    public val misses: List<String>,
    public val extras: List<String>,
) {
    /** True when nothing is missing and nothing is extra. */
    public val isExact: Boolean get() = misses.isEmpty() && extras.isEmpty()

    public companion object {
        internal fun of(
            matched: Int,
            missed: Int,
            extra: Int,
            misses: List<String> = emptyList(),
            extras: List<String> = emptyList(),
        ): Score {
            val expected = matched + missed
            val produced = matched + extra
            val precision = ratio(matched, produced)
            val recall = ratio(matched, expected)
            val f1 = if (precision + recall == 0.0) 0.0 else 2 * precision * recall / (precision + recall)
            return Score(expected, produced, matched, precision, recall, f1, misses, extras)
        }

        /** The micro average: counts are added, and the lists of misses and extras are concatenated. */
        public fun combine(scores: List<Score>): Score = of(
            matched = scores.sumOf { it.matched },
            missed = scores.sumOf { it.expected - it.matched },
            extra = scores.sumOf { it.produced - it.matched },
            misses = scores.flatMap { it.misses },
            extras = scores.flatMap { it.extras },
        )

        private fun ratio(part: Int, whole: Int): Double = if (whole == 0) 1.0 else part.toDouble() / whole
    }
}
