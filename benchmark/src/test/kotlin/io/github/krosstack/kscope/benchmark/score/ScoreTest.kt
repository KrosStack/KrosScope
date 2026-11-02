package io.github.krosstack.kscope.benchmark.score

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScoreTest {
    private fun items(vararg keys: String) = keys.map { Item(it, "label $it") }

    @Test
    fun `precision and recall count matched elements over produced and expected ones`() {
        val score = Matching.of(items("a", "b", "c", "d"), items("a", "b", "x")).score()
        assertEquals(2, score.matched)
        assertEquals(4, score.expected)
        assertEquals(3, score.produced)
        assertEquals(2.0 / 3, score.precision, TOLERANCE)
        assertEquals(0.5, score.recall, TOLERANCE)
        assertEquals(2 * (2.0 / 3) * 0.5 / (2.0 / 3 + 0.5), score.f1, TOLERANCE)
    }

    @Test
    fun `misses and extras are listed by label in sorted order`() {
        val score = Matching.of(items("b", "a"), items("c", "a")).score()
        assertEquals(listOf("label b"), score.misses)
        assertEquals(listOf("label c"), score.extras)
    }

    @Test
    fun `an element expected twice has to be produced twice`() {
        val once = Matching.of(items("a", "a"), items("a")).score()
        assertEquals(1, once.matched)
        assertEquals(listOf("label a"), once.misses)
        val twice = Matching.of(items("a", "a"), items("a", "a")).score()
        assertTrue(twice.isExact)
    }

    @Test
    fun `an element produced twice but expected once is an extra`() {
        val score = Matching.of(items("a"), items("a", "a")).score()
        assertEquals(listOf("label a"), score.extras)
        assertEquals(0.5, score.precision, TOLERANCE)
        assertEquals(1.0, score.recall, TOLERANCE)
    }

    @Test
    fun `a side without elements has a ratio of one and nothing matched has an f1 of zero`() {
        val nothingExpected = Matching.of(emptyList(), items("a")).score()
        assertEquals(1.0, nothingExpected.recall, TOLERANCE)
        assertEquals(0.0, nothingExpected.precision, TOLERANCE)
        assertEquals(0.0, nothingExpected.f1, TOLERANCE)
        val nothingProduced = Matching.of(items("a"), emptyList()).score()
        assertEquals(1.0, nothingProduced.precision, TOLERANCE)
        assertEquals(0.0, nothingProduced.recall, TOLERANCE)
    }

    @Test
    fun `matched pairs keep the group of the expected and of the produced element`() {
        val expected = listOf(Item("a", "a", group = "compiler"))
        val produced = listOf(Item("a", "a", group = "convention"))
        val pair = Matching.of(expected, produced).pairs.single()
        assertEquals("compiler" to "convention", pair.first.group to pair.second.group)
    }

    @Test
    fun `combining scores adds the counts and recomputes the ratios`() {
        val first = Matching.of(items("a", "b"), items("a")).score()
        val second = Matching.of(items("c"), items("c", "d")).score()
        val combined = Score.combine(listOf(first, second))
        assertEquals(2, combined.matched)
        assertEquals(3, combined.expected)
        assertEquals(3, combined.produced)
        assertEquals(listOf("label b", "label d"), combined.misses + combined.extras)
        assertEquals(2.0 / 3, combined.recall, TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
