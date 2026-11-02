package io.github.krosstack.kscope.context.selection

import io.github.krosstack.kscope.context.ContextMarkdown
import io.github.krosstack.kscope.context.TokenEstimators
import io.github.krosstack.kscope.context.token.BlockAdditive
import kotlin.test.Test
import kotlin.test.assertEquals

internal class TokenTallyTest {
    @Test
    fun `a tally counts the blocks it stands for at every unresolved count`() {
        for (id in TokenEstimators.supported) {
            val additive = TokenEstimators.create(id) as BlockAdditive
            val tally = TokenTally(additive, TASK)
            val taskMeasure = additive.measure(requireNotNull(ContextMarkdown.taskBlock(TASK)))
            for (count in -3..MAXIMUM_COUNT) {
                val unresolved = ContextMarkdown.unresolvedBlock(count)?.let(additive::measure) ?: 0L
                val expected = additive.tokens(taskMeasure + FILE_MEASURE + unresolved)

                assertEquals(expected, tally.tokens(FILE_MEASURE, count), "estimator=${id.value} count=$count")
            }
        }
    }

    @Test
    fun `a tally measures the unresolved block of each count once and none for a count of zero or less`() {
        val counting = AdditiveCountingEstimator(TokenEstimators.create())
        val tally = TokenTally(counting, TASK)
        val afterTask = counting.snapshot()

        tally.tokens(FILE_MEASURE, 0)
        tally.tokens(FILE_MEASURE, -4)
        assertEquals(0, (counting.snapshot() - afterTask).total)

        repeat(REPEATS) { tally.tokens(FILE_MEASURE + it, FIRST_COUNT) }
        assertEquals(1, (counting.snapshot() - afterTask).total)

        tally.tokens(FILE_MEASURE, SECOND_COUNT)
        tally.tokens(FILE_MEASURE, FIRST_COUNT)
        assertEquals(2, (counting.snapshot() - afterTask).total)
    }

    private companion object {
        const val TASK = "change the member values"
        const val FILE_MEASURE = 1_234L
        const val MAXIMUM_COUNT = 120
        const val REPEATS = 5
        const val FIRST_COUNT = 12
        const val SECOND_COUNT = 13
    }
}
