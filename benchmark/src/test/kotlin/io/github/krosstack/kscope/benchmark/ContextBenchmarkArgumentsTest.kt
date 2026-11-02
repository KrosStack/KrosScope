package io.github.krosstack.kscope.benchmark

import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkBudgets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ContextBenchmarkArgumentsTest {
    private val base = listOf("context-benchmark", "--mode", "default", "--out", "output")

    @Test
    fun `the budget set defaults to the four standard budgets`() {
        val arguments = assertNotNull(ContextBenchmarkArguments.parse(base + "--original-only"))

        assertEquals(ContextBenchmarkBudgets.values, arguments.budgets)
        assertNull(arguments.options.heldOut)
        assertNull(arguments.checkpointKey)
    }

    @Test
    fun `a budget subset composes with original only and the checkpoint key`() {
        val args = base + listOf("--original-only", "--budgets", "4000,2000", "--checkpoint-key", "abc")

        val arguments = assertNotNull(ContextBenchmarkArguments.parse(args))

        assertEquals(listOf(2_000, 4_000), arguments.budgets.map { it.value })
        assertEquals("abc", arguments.checkpointKey)
        assertNull(arguments.options.heldOut)
    }

    @Test
    fun `unknown repeated or incomplete budget lists are rejected`() {
        val invalid = listOf(
            base + listOf("--budgets", "3000"),
            base + listOf("--budgets", "2000,2000"),
            base + listOf("--budgets"),
            base + listOf("--budgets", "--original-only"),
            base + listOf("--budgets", "2000", "--budgets", "4000"),
        )

        invalid.forEach { assertNull(ContextBenchmarkArguments.parse(it), it.joinToString(" ")) }
    }

    @Test
    fun `budget parsing sorts accepted lists and rejects anything else`() {
        assertEquals(listOf(2_000, 16_000), ContextBenchmarkBudgets.parse("16000,2000")?.map { it.value })
        listOf("", ",", "2000,", "1", "2000 4000", "-2000").forEach {
            assertNull(ContextBenchmarkBudgets.parse(it), it)
        }
    }
}
