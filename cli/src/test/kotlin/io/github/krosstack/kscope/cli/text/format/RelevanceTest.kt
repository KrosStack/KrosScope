package io.github.krosstack.kscope.cli.text.format

import kotlin.test.Test
import kotlin.test.assertEquals

class RelevanceTest {
    private fun relevant(scores: List<Double>, pinned: (Double) -> Boolean = { false }): List<Double> =
        RelevanceCutoff.select(scores, pinned) { it }.relevant

    @Test
    fun `an item is relevant when it scores at least thirty percent of the best`() {
        val scores = listOf(1.0, 0.9, 0.5, 0.31, 0.3, 0.29, 0.1)

        assertEquals(listOf(1.0, 0.9, 0.5, 0.31, 0.3), relevant(scores))
    }

    @Test
    fun `the three best are relevant whatever they score`() {
        assertEquals(listOf(1.0, 0.05, 0.04), relevant(listOf(1.0, 0.05, 0.04, 0.03, 0.02)))
        assertEquals(listOf(1.0), relevant(listOf(1.0)))
        assertEquals(emptyList(), relevant(emptyList()))
    }

    @Test
    fun `a pinned item always counts and is not the measure of the others`() {
        val scores = listOf(10.0, 0.10, 0.05, 0.04, 0.02, 0.01)

        assertEquals(listOf(10.0, 0.10, 0.05, 0.04), relevant(scores) { it == 10.0 })
    }

    @Test
    fun `the selection knows how many were scored and how many fell below`() {
        val selection = RelevanceCutoff.select(listOf(1.0, 0.9, 0.8, 0.5, 0.1, 0.05)) { it }

        assertEquals(6, selection.scored)
        assertEquals(2, selection.belowCutoff)
        assertEquals("30%", RelevanceCutoff.percent())
    }

    @Test
    fun `the second candidate decides between one clear match and several close candidates`() {
        assertEquals(MatchStrength.CLEAR, MatchStrength.of(listOf(0.223, 0.129, 0.12)))
        assertEquals(MatchStrength.CLEAR, MatchStrength.of(listOf(0.5)))
        assertEquals(MatchStrength.CLOSE, MatchStrength.of(listOf(0.124, 0.122, 0.116, 0.05)))
        assertEquals(MatchStrength.CLOSE, MatchStrength.of(listOf(0.1, 0.0651)))
        assertEquals(MatchStrength.CLEAR, MatchStrength.of(listOf(0.1, 0.065)))
    }

    @Test
    fun `scores that crowd the best make a result without a strong match`() {
        val crowd = List(MatchStrength.CROWD) { 0.1 - it * 0.001 }

        assertEquals(MatchStrength.NONE, MatchStrength.of(crowd))
        assertEquals(MatchStrength.CLOSE, MatchStrength.of(crowd.dropLast(1)))
        assertEquals(MatchStrength.NONE, MatchStrength.of(emptyList()))
    }

    @Test
    fun `the wording of each strength is the one the documentation promises`() {
        assertEquals("one clear match", MatchStrength.CLEAR.wording)
        assertEquals("several close candidates", MatchStrength.CLOSE.wording)
        assertEquals("no strong match", MatchStrength.NONE.wording)
    }
}
