package io.github.krosstack.kscope.context

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.context.ContextCandidate
import io.github.krosstack.kscope.model.context.ContextInput
import io.github.krosstack.kscope.model.context.TokenCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ContextScaleTest {
    @Test
    fun `one hundred independent declarations preserve the final two thousand token cap`() {
        val lines = (0 until 100).map { "fun member$it(): Int = $it" }
        val text = lines.joinToString("\n", postfix = "\n")
        val file = sourceArtifact("source/Scale", text)
        val symbols = lines.mapIndexed { index, line ->
            declaration(
                "member$index",
                file,
                TextRange(index + 1, 1, index + 1, line.length + 1),
                "fun member$index(): Int",
            )
        }
        val fixture = ContextFixture(listOf(file), symbols, mapOf(file.ref to text))
        val input = ContextInput("fix members", symbols.map { ContextCandidate(NodeRef.OfSymbol(it.id), 1.0) })
        val estimator = TokenEstimators.create()

        val result = ContextCompiler(fixture, fixture, estimator).compile(input, budget = TokenCount(2000))

        assertTrue(result.fragments.isNotEmpty())
        assertTrue(result.totalTokens.value <= 2000)
        assertEquals(estimator.count(result.markdown), result.totalTokens)
    }
}
